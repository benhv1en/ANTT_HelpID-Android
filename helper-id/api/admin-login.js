const MAX_EMAIL_LENGTH = 256;
const MAX_PASSWORD_LENGTH = 1000;

function setSecurityHeaders(res) {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Pragma', 'no-cache');
  res.setHeader('Expires', '0');
  res.setHeader('X-Robots-Tag', 'noindex, nofollow, noarchive');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
}

function getBackendUrl() {
  const url = process.env.HELPID_BACKEND_URL;
  if (!url) throw new Error('Missing HELPID_BACKEND_URL env var');
  return url.replace(/\/$/, '');
}

function logSafeError(scope, error) {
  const name = typeof error?.name === 'string' ? error.name : 'Error';
  console.error(`${scope} failed`, { name });
}

function sanitizeLoginResponse(json) {
  return {
    accessToken: typeof json?.accessToken === 'string' ? json.accessToken : '',
    refreshToken: typeof json?.refreshToken === 'string' ? json.refreshToken : '',
    accessTokenExpiresAtUtc:
      typeof json?.accessTokenExpiresAtUtc === 'string' ? json.accessTokenExpiresAtUtc : '',
    refreshTokenExpiresAtUtc:
      typeof json?.refreshTokenExpiresAtUtc === 'string' ? json.refreshTokenExpiresAtUtc : '',
    user: {
      id: typeof json?.user?.id === 'string' ? json.user.id : '',
      email: typeof json?.user?.email === 'string' ? json.user.email : '',
      displayName: typeof json?.user?.displayName === 'string' ? json.user.displayName : '',
      roles: Array.isArray(json?.user?.roles)
        ? json.user.roles.filter((r) => typeof r === 'string')
        : [],
      permissions: Array.isArray(json?.user?.permissions)
        ? json.user.permissions.filter((p) => typeof p === 'string')
        : [],
    },
  };
}

export default async function handler(req, res) {
  try {
    if (req.method !== 'POST') {
      setSecurityHeaders(res);
      res.statusCode = 405;
      res.setHeader('Allow', 'POST');
      res.end(JSON.stringify({ error: 'Method not allowed' }));
      return;
    }

    const email = typeof req.body?.email === 'string' ? req.body.email.trim() : '';
    const password = typeof req.body?.password === 'string' ? req.body.password : '';

    if (!email || !password) {
      setSecurityHeaders(res);
      res.statusCode = 400;
      res.end(JSON.stringify({ error: 'Email and password are required' }));
      return;
    }

    if (email.length > MAX_EMAIL_LENGTH || password.length > MAX_PASSWORD_LENGTH) {
      setSecurityHeaders(res);
      res.statusCode = 400;
      res.end(JSON.stringify({ error: 'Invalid request' }));
      return;
    }

    const backendUrl = getBackendUrl();

    let backendRes;
    try {
      backendRes = await fetch(`${backendUrl}/api/v1/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          'Cache-Control': 'no-store',
        },
        body: JSON.stringify({ email, password }),
        signal: AbortSignal.timeout(10_000),
      });
    } catch {
      setSecurityHeaders(res);
      res.statusCode = 503;
      res.end(JSON.stringify({ error: 'Service temporarily unavailable' }));
      return;
    }

    const json = await backendRes.json().catch(() => ({}));

    if (!backendRes.ok) {
      const errMsg =
        backendRes.status === 401
          ? 'Invalid credentials'
          : json?.title || json?.error || 'Login failed';
      setSecurityHeaders(res);
      res.statusCode = backendRes.status;
      res.end(JSON.stringify({ error: errMsg }));
      return;
    }

    setSecurityHeaders(res);
    res.statusCode = 200;
    res.end(JSON.stringify(sanitizeLoginResponse(json)));
  } catch (e) {
    logSafeError('admin-login handler', e);
    setSecurityHeaders(res);
    res.statusCode = 500;
    res.end(JSON.stringify({ error: 'Internal error' }));
  }
}
