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

export default async function handler(req, res) {
  try {
    if (req.method !== 'POST') {
      setSecurityHeaders(res);
      res.statusCode = 405;
      res.setHeader('Allow', 'POST');
      res.end(JSON.stringify({ error: 'Method not allowed' }));
      return;
    }

    const authorization =
      typeof req.headers?.authorization === 'string' ? req.headers.authorization : '';

    if (!authorization) {
      setSecurityHeaders(res);
      res.statusCode = 401;
      res.end(JSON.stringify({ error: 'Unauthorized' }));
      return;
    }

    const backendUrl = getBackendUrl();

    const refreshToken =
      typeof req.body?.refreshToken === 'string' ? req.body.refreshToken : null;

    try {
      await fetch(`${backendUrl}/api/v1/auth/logout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          Authorization: authorization,
          'Cache-Control': 'no-store',
        },
        body: JSON.stringify({ refreshToken }),
        signal: AbortSignal.timeout(10_000),
      });
    } catch {
      // Best-effort revoke: even if backend is unreachable, return 200 so
      // the client proceeds to clear its session.
    }

    setSecurityHeaders(res);
    res.statusCode = 200;
    res.end(JSON.stringify({ ok: true }));
  } catch (e) {
    logSafeError('admin-logout handler', e);
    setSecurityHeaders(res);
    res.statusCode = 500;
    res.end(JSON.stringify({ error: 'Internal error' }));
  }
}
