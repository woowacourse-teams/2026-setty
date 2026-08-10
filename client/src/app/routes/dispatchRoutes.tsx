import { useCallback } from 'react';
import {
  Navigate,
  type RouteObject,
  useLocation,
  useNavigate,
  useParams,
} from 'react-router-dom';
import OnboardingGate from '@/app/onboarding/OnboardingGate';
import BuyerRequestFormScreen from '@/flows/dispatch/screens/BuyerRequestFormScreen';
import DispatchIntroScreen from '@/flows/dispatch/screens/DispatchIntroScreen';
import FinalAmountConfirmScreen from '@/flows/dispatch/screens/FinalAmountConfirmScreen';
import LinkCreatedScreen from '@/flows/dispatch/screens/LinkCreatedScreen';
import SellerInputFormScreen from '@/flows/dispatch/screens/SellerInputFormScreen';
import SellerSubmittedScreen from '@/flows/dispatch/screens/SellerSubmittedScreen';
import SellerWaitingScreen from '@/flows/dispatch/screens/SellerWaitingScreen';

/**
 * 배차 flow의 모든 화면 전환은 URL로 표현한다.
 * 화면 단계를 컴포넌트 state로만 들고 있으면 히스토리 항목이 쌓이지 않아
 * 브라우저 뒤로가기가 이전 단계 대신 사이트 밖으로 나간다.
 */
export const DISPATCH_PATH = {
  home: '/',
  buyerForm: '/dispatch/new',
  linkCreated: (buyerToken: string) =>
    `/dispatch/${encodeURIComponent(buyerToken)}/link`,
  buyerStatus: (buyerToken: string) => `/dispatch/${encodeURIComponent(buyerToken)}`,
  sellerInput: (sellerToken: string) => `/seller-input/${encodeURIComponent(sellerToken)}`,
  sellerSubmitted: (sellerToken: string) =>
    `/seller-input/${encodeURIComponent(sellerToken)}/submitted`,
} as const;

/** 예상 견적은 estimate flow 소유다. dispatch는 코드를 import하지 않고 경로만 참조한다. */
const ESTIMATE_PATH = '/estimate';

/**
 * 링크 생성 직후에는 생성 응답의 링크를 그대로 쓰고 재조회하지 않는다.
 * 새로고침·뒤로가기로 navigation state가 사라진 경우에만 buyerToken으로 복구한다.
 */
interface LinkCreatedState {
  sellerInputUrl: string;
}

const readSellerInputUrl = (state: unknown): string | undefined => {
  if (typeof state !== 'object' || state === null) {
    return undefined;
  }

  const { sellerInputUrl } = state as Partial<LinkCreatedState>;

  return typeof sellerInputUrl === 'string' ? sellerInputUrl : undefined;
};

/**
 * 앱 안에서 눌러 들어온 화면이면 히스토리를 실제로 되감고,
 * 링크로 바로 들어온 첫 화면이면 뒤로가기가 사이트를 벗어나므로 fallback 경로로 보낸다.
 */
function useGoBack(fallbackPath: string) {
  const navigate = useNavigate();
  const { key } = useLocation();
  // React Router는 직접 진입한 첫 항목의 key를 'default'로 둔다.
  const isFirstEntry = key === 'default';

  return useCallback(() => {
    if (isFirstEntry) {
      navigate(fallbackPath, { replace: true });
      return;
    }

    navigate(-1);
  }, [fallbackPath, isFirstEntry, navigate]);
}

function DispatchIntroRoute() {
  const navigate = useNavigate();

  return (
    <DispatchIntroScreen
      onCreateLink={() => navigate(DISPATCH_PATH.buyerForm)}
      onCheckEstimate={() => navigate(ESTIMATE_PATH)}
    />
  );
}

/**
 * 동의 안내 화면은 같은 경로에 history 항목만 더해 연다.
 * 별도 경로로 이동하면 폼이 언마운트돼 입력값이 사라지고,
 * 화면 state로만 열면 히스토리가 쌓이지 않아 브라우저 뒤로가기가 사이트 밖으로 나간다.
 */
interface PrivacyNoticeState {
  privacyNotice?: boolean;
}

function BuyerRequestFormRoute() {
  const navigate = useNavigate();
  const { state } = useLocation();
  const goBack = useGoBack(DISPATCH_PATH.home);
  const isPrivacyNoticeOpen = (state as PrivacyNoticeState | null)?.privacyNotice === true;

  return (
    <BuyerRequestFormScreen
      onBack={goBack}
      isPrivacyNoticeOpen={isPrivacyNoticeOpen}
      onOpenPrivacyNotice={() =>
        navigate(DISPATCH_PATH.buyerForm, {
          state: { privacyNotice: true } satisfies PrivacyNoticeState,
        })
      }
      onClosePrivacyNotice={() => navigate(-1)}
      onCreated={(result) =>
        /*
         * 제출한 폼을 히스토리에 남기지 않는다.
         * 남겨두면 뒤로가기로 돌아가 같은 거래 요청을 한 번 더 만들 수 있다.
         */
        navigate(DISPATCH_PATH.linkCreated(result.buyerToken), {
          replace: true,
          state: { sellerInputUrl: result.sellerInputUrl } satisfies LinkCreatedState,
        })
      }
    />
  );
}

function LinkCreatedRoute() {
  const navigate = useNavigate();
  const { buyerToken } = useParams<{ buyerToken: string }>();
  const { state } = useLocation();

  if (!buyerToken) {
    return <Navigate to={DISPATCH_PATH.home} replace />;
  }

  return (
    <LinkCreatedScreen
      key={buyerToken}
      buyerToken={buyerToken}
      initialSellerInputUrl={readSellerInputUrl(state)}
      onNext={() => navigate(DISPATCH_PATH.buyerStatus(buyerToken))}
    />
  );
}

/**
 * 계정이 없는 MVP에서 buyerToken 자체가 구매자 요청 조회 권한이다.
 * URL로 복구해 React 메모리가 사라지는 새로고침·재방문에서도 같은 카드를 연다.
 */
function BuyerStatusRoute() {
  const navigate = useNavigate();
  const { buyerToken } = useParams<{ buyerToken: string }>();

  if (!buyerToken) {
    return <Navigate to={DISPATCH_PATH.home} replace />;
  }

  return (
    <SellerWaitingScreen
      key={buyerToken}
      buyerToken={buyerToken}
      onGoHome={() => navigate(DISPATCH_PATH.home)}
    />
  );
}

function SellerInputFormRoute() {
  const navigate = useNavigate();
  const { sellerToken } = useParams<{ sellerToken: string }>();
  const { state } = useLocation();
  const isPrivacyNoticeOpen = (state as PrivacyNoticeState | null)?.privacyNotice === true;

  if (!sellerToken) {
    return <Navigate to={DISPATCH_PATH.home} replace />;
  }

  return (
    <SellerInputFormScreen
      key={sellerToken}
      sellerToken={sellerToken}
      isPrivacyNoticeOpen={isPrivacyNoticeOpen}
      onOpenPrivacyNotice={() =>
        navigate(DISPATCH_PATH.sellerInput(sellerToken), {
          state: { privacyNotice: true } satisfies PrivacyNoticeState,
        })
      }
      onClosePrivacyNotice={() => navigate(-1)}
      onSubmitted={() =>
        // 제출 폼으로 되돌아가 중복 제출(server 409)을 시도하게 두지 않는다.
        navigate(DISPATCH_PATH.sellerSubmitted(sellerToken), { replace: true })
      }
    />
  );
}

function SellerSubmittedRoute() {
  const navigate = useNavigate();

  return <SellerSubmittedScreen onGoHome={() => navigate(DISPATCH_PATH.home)} />;
}

function FinalAmountConfirmRoute() {
  const navigate = useNavigate();
  const { buyerToken } = useParams<{ buyerToken: string }>();

  if (!buyerToken) {
    return <Navigate to={DISPATCH_PATH.home} replace />;
  }

  return (
    <FinalAmountConfirmScreen
      key={buyerToken}
      buyerToken={buyerToken}
      onGoHome={() => navigate(DISPATCH_PATH.home)}
    />
  );
}

/**
 * `/dispatch/new`와 `/dispatch/:buyerToken`은 배열 순서와 무관하게
 * React Router가 정적 segment를 먼저 고르므로 폼 경로가 토큰으로 해석되지 않는다.
 */
export const dispatchRoutes: RouteObject[] = [
  {
    /*
     * 홈은 앱의 첫 진입점이라 처음 온 기기에만 온보딩을 먼저 보여준다.
     * 토큰 링크로 바로 들어오는 아래 경로들은 감싸지 않는다.
     */
    path: '/',
    element: (
      <OnboardingGate>
        <DispatchIntroRoute />
      </OnboardingGate>
    ),
  },
  {
    path: '/dispatch/new',
    element: <BuyerRequestFormRoute />,
  },
  {
    path: '/dispatch/:buyerToken/link',
    element: <LinkCreatedRoute />,
  },
  {
    path: '/dispatch/:buyerToken',
    element: <BuyerStatusRoute />,
  },
  {
    path: '/seller-input/:sellerToken',
    element: <SellerInputFormRoute />,
  },
  {
    path: '/seller-input/:sellerToken/submitted',
    element: <SellerSubmittedRoute />,
  },
  {
    path: '/final-amount/:buyerToken',
    element: <FinalAmountConfirmRoute />,
  },
];
