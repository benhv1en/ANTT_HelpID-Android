const PUBLIC_KEY_REGEX = /^HID-[A-Z0-9_-]{8,64}$/;
const MAX_STRING_LENGTH = 500;
const MAX_LIST_ITEMS = 50;

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

function pickString(value) {
  if (typeof value !== 'string') return '';
  return value.slice(0, MAX_STRING_LENGTH);
}

function pickStringList(value) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item) => typeof item === 'string')
    .slice(0, MAX_LIST_ITEMS)
    .map((item) => item.slice(0, MAX_STRING_LENGTH));
}

function pickContacts(value) {
  if (!Array.isArray(value)) return [];
  return value
    .slice(0, MAX_LIST_ITEMS)
    .map((contact) => ({
      name: pickString(contact?.name),
      phone: pickString(contact?.phone),
    }))
    .filter((contact) => contact.name || contact.phone);
}

function sanitizeProfile(profile) {
  return {
    name: pickString(profile?.name),
    bloodGroup: pickString(profile?.bloodGroup),
    allergies: pickStringList(profile?.allergies),
    emergencyContacts: pickContacts(profile?.emergencyContacts),
    address: pickString(profile?.address),
    medicalNotes: pickStringList(profile?.medicalNotes),
  };
}

function logSafeError(scope, error) {
  const name = typeof error?.name === 'string' ? error.name : 'Error';
  console.error(`${scope} failed`, { name });
}

export default async function handler(req, res) {
  try {
    if (req.method !== 'GET') {
      res.statusCode = 405;
      res.setHeader('Allow', 'GET');
      setSecurityHeaders(res);
      res.end(JSON.stringify({ error: 'Method not allowed' }));
      return;
    }

    const key = typeof req.query?.key === 'string' ? req.query.key : '';
    const token = typeof req.query?.t === 'string' ? req.query.t : '';

    if (!key || !token) {
      res.statusCode = 400;
      setSecurityHeaders(res);
      res.end(JSON.stringify({ error: 'Missing key or token' }));
      return;
    }

    if (!PUBLIC_KEY_REGEX.test(key) || token.length > 4096) {
      res.statusCode = 400;
      setSecurityHeaders(res);
      res.end(JSON.stringify({ error: 'Invalid request' }));
      return;
    }

    const backendUrl = getBackendUrl();
    const upstream = `${backendUrl}/api/v1/public/profile?key=${encodeURIComponent(key)}&t=${encodeURIComponent(token)}`;

    let backendRes;
    try {
      backendRes = await fetch(upstream, {
        method: 'GET',
        headers: {
          Accept: 'application/json',
          'Cache-Control': 'no-store',
          Pragma: 'no-cache',
        },
        signal: AbortSignal.timeout(10_000),
      });
    } catch {
      res.statusCode = 503;
      setSecurityHeaders(res);
      res.end(JSON.stringify({ error: 'Service temporarily unavailable' }));
      return;
    }

    const json = await backendRes.json().catch(() => ({}));

    if (!backendRes.ok) {
      const errMsg = backendRes.status === 401 || backendRes.status === 403
        ? 'Invalid or expired token'
        : (json?.title || json?.error || 'Failed to load profile');
      res.statusCode = backendRes.status;
      setSecurityHeaders(res);
      res.end(JSON.stringify({ error: errMsg }));
      return;
    }

    setSecurityHeaders(res);
    res.statusCode = 200;
    res.end(JSON.stringify({ key, profile: sanitizeProfile(json?.profile) }));
  } catch (e) {
    logSafeError('profile handler', e);
    setSecurityHeaders(res);
    res.statusCode = 500;
    res.end(JSON.stringify({ error: 'Internal error' }));
  }
}
