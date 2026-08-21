import { ReactNode } from 'react';
import AuthModal from './AuthModal';

interface AuthGateProps {
  authenticationRequired: boolean;
  children: ReactNode;
  onCancel: () => void;
  onAuthenticated: () => void | Promise<void>;
}

export function AuthGate({
  authenticationRequired,
  children,
  onCancel,
  onAuthenticated,
}: AuthGateProps) {
  return (
    <>
      <div
        aria-hidden={authenticationRequired || undefined}
        inert={authenticationRequired || undefined}
      >
        {children}
      </div>
      <AuthModal
        open={authenticationRequired}
        onCancel={onCancel}
        onAuthenticated={onAuthenticated}
      />
    </>
  );
}

export function isAuthenticationError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    error.status === 401
  );
}

export default AuthGate;
