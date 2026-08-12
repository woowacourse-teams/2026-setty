import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { hasOperatorSecret } from './operatorSecretStorage';

export default function OperatorProtectedRoute() {
  const location = useLocation();

  if (!hasOperatorSecret()) {
    return (
      <Navigate
        replace
        state={{ from: `${location.pathname}${location.search}` }}
        to="/operator/login"
      />
    );
  }

  return <Outlet />;
}
