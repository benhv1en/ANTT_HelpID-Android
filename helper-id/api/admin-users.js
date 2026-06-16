const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 20;
const MAX_SIZE = 100;

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
    if (req.method !== 'GET') {
      setSecurityHeaders(res);
      res.statusCode = 405;
      res.setHeader('Allow', 'GET');
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

    const rawPage = parseInt(req.query?.page, 10);
    const rawSize = parseInt(req.query?.size, 10);
    const page = Number.isFinite(rawPage) && rawPage >= 1 ? rawPage : DEFAULT_PAGE;
    const size =
      Number.isFinite(rawSize) && rawSize >= 1 && rawSize <= MAX_SIZE ? rawSize : DEFAULT_SIZE;

    const backendUrl = getBackendUrl();
    const upstream = `${backendUrl}/api/v1/admin/users?page=${page}&size=${size}`;

    let backendRes;
    try {
      backendRes = await fetch(upstream, {
        method: 'GET',
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

    const json = await backendRes.json().catch(() => ({}));

    setSecurityHeaders(res);
    res.statusCode = backendRes.status;
    res.end(JSON.stringify(json));
  } catch (e) {
    logSafeError('admin-users handler', e);
    setSecurityHeaders(res);
    res.statusCode = 500;
    res.end(JSON.stringify({ error: 'Internal error' }));
  }
}
