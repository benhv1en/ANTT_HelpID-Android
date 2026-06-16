const ALLOWED_ROLE_IDS = new Set(['role_user', 'role_admin']);
const MAX_USER_ID_LENGTH = 64;

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
    if (req.method !== 'POST' && req.method !== 'DELETE') {
      setSecurityHeaders(res);
      res.statusCode = 405;
      res.setHeader('Allow', 'POST, DELETE');
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

    const userId = typeof req.query?.userId === 'string' ? req.query.userId.trim() : '';
    const roleId = typeof req.query?.roleId === 'string' ? req.query.roleId.trim() : '';

    if (!userId || userId.length > MAX_USER_ID_LENGTH) {
      setSecurityHeaders(res);
      res.statusCode = 400;
      res.end(JSON.stringify({ error: 'Invalid userId' }));
      return;
    }

    if (!ALLOWED_ROLE_IDS.has(roleId)) {
      setSecurityHeaders(res);
      res.statusCode = 400;
      res.end(JSON.stringify({ error: 'Invalid roleId' }));
      return;
    }

    const backendUrl = getBackendUrl();
    const upstream = `${backendUrl}/api/v1/admin/users/${encodeURIComponent(userId)}/roles/${encodeURIComponent(roleId)}`;

    let backendRes;
    try {
      backendRes = await fetch(upstream, {
        method: req.method,
        headers: {
          Accept: 'application/json',
          Authorization: authorization,
          'Cache-Control': 'no-store',
        },
        signal: AbortSignal.timeout(10_000),
      });
    } catch {
      setSecurityHeaders(res);
      res.statusCode = 503;
      res.end(JSON.stringify({ error: 'Service temporarily unavailable' }));
      return;
    }

    setSecurityHeaders(res);
    res.statusCode = backendRes.status;

    if (backendRes.status === 204 || backendRes.headers.get('content-length') === '0') {
      res.end('{}');
      return;
    }

    const json = await backendRes.json().catch(() => ({}));
    res.end(JSON.stringify(json));
  } catch (e) {
    logSafeError('admin-role handler', e);
    setSecurityHeaders(res);
    res.statusCode = 500;
    res.end(JSON.stringify({ error: 'Internal error' }));
  }
}
