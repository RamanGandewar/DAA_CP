const map = L.map('map').setView([20.5937, 78.9629], 5);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap contributors'
}).addTo(map);

let markers = [];
const markerCluster = L.markerClusterGroup({ spiderfyOnMaxZoom: true, showCoverageOnHover: false });
map.addLayer(markerCluster);
let radiusCircle = null;
let userLocationMarker = null;
let userAccuracyCircle = null;
let cafeMarkersById = new Map();
let hasResolvedLocation = false;
let locationWatchId = null;
let locationCaptureSource = '';

const PROFILE_CACHE_KEY = 'cafe_user_profile_v2';
const USER_KEY_STORAGE = 'cafe_user_key_v1';
const LOCATION_CACHE_KEY = 'cafe_last_location_v1';
const AUTH_SESSION_KEY = 'cafe_auth_session_v1';
let currentSession = null;

function q(id) {
  const node = document.getElementById(id);
  if (!node) return '';
  return node.value.trim();
}

function clearMap() {
  markers.forEach(m => markerCluster.removeLayer(m));
  markers = [];
  cafeMarkersById = new Map();
  if (radiusCircle) {
    map.removeLayer(radiusCircle);
    radiusCircle = null;
  }
}

function addMarker(id, lat, lon, popupHtml) {
  const marker = L.marker([lat, lon]).bindPopup(popupHtml);
  markerCluster.addLayer(marker);
  markers.push(marker);
  if (id) {
    cafeMarkersById.set(id, marker);
  }
  return marker;
}

function focusCafeOnMap(cafeId) {
  const marker = cafeMarkersById.get(cafeId);
  if (!marker) return;
  const position = marker.getLatLng();
  map.setView(position, 16);
  markerCluster.zoomToShowLayer(marker, () => marker.openPopup());
}

function fitMapToVisiblePoints(results) {
  const points = [];
  if (userLocationMarker) {
    points.push(userLocationMarker.getLatLng());
  }
  results.forEach(r => {
    if (Number.isFinite(r.latitude) && Number.isFinite(r.longitude)) {
      points.push(L.latLng(r.latitude, r.longitude));
    }
  });
  if (points.length >= 2) {
    map.fitBounds(L.latLngBounds(points), { padding: [40, 40] });
  }
}

function focusOnUserLocation(zoomLevel) {
  if (!userLocationMarker) {
    setStatus('Live location is not available yet.');
    return;
  }
  const position = userLocationMarker.getLatLng();
  map.flyTo(position, zoomLevel || 15, { animate: true, duration: 0.8 });
  userLocationMarker.openPopup();
}

function setStatus(message) {
  document.getElementById('status').textContent = message;
}

function setLocationSummary(message) {
  const node = document.getElementById('locationSummary');
  if (node) {
    node.textContent = message;
  }
}

function setProfileStatus(message) {
  const summary = document.getElementById('profileSummary');
  if (summary) {
    summary.textContent = message;
  }
}

function setAuthBanner(message) {
  const banner = document.getElementById('authBanner');
  if (banner) {
    banner.textContent = message;
  }
}

function getStoredSession() {
  try {
    const raw = localStorage.getItem(AUTH_SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (err) {
    return null;
  }
}

function saveStoredSession(payload) {
  currentSession = payload;
  localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(payload));
}

function clearStoredSession() {
  currentSession = null;
  localStorage.removeItem(AUTH_SESSION_KEY);
}

function clearUserLocationVisuals() {
  if (userLocationMarker) {
    map.removeLayer(userLocationMarker);
    userLocationMarker = null;
  }
  if (userAccuracyCircle) {
    map.removeLayer(userAccuracyCircle);
    userAccuracyCircle = null;
  }
}

function createUserLocationMarker(lat, lon) {
  const pulseIcon = L.divIcon({
    className: 'user-location-pulse-wrapper',
    html: '<div class="user-location-pulse"></div>',
    iconSize: [22, 22],
    iconAnchor: [11, 11]
  });
  return L.marker([lat, lon], { icon: pulseIcon, keyboard: false });
}

function saveLocationCache(lat, lon, accuracyMeters, source) {
  const payload = {
    lat: Number(lat),
    lon: Number(lon),
    accuracyMeters: Number(accuracyMeters || 0),
    source: source || 'live',
    capturedAt: new Date().toISOString()
  };
  localStorage.setItem(LOCATION_CACHE_KEY, JSON.stringify(payload));
}

function loadLocationCache() {
  try {
    const raw = localStorage.getItem(LOCATION_CACHE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (err) {
    return null;
  }
}

function setUserLocation(lat, lon, accuracyMeters, source) {
  document.getElementById('lat').value = Number(lat).toFixed(6);
  document.getElementById('lon').value = Number(lon).toFixed(6);
  hasResolvedLocation = true;
  locationCaptureSource = source || 'live';
  saveLocationCache(lat, lon, accuracyMeters, source);

  clearUserLocationVisuals();
  userAccuracyCircle = L.circle([lat, lon], {
    radius: Math.max(accuracyMeters || 40, 25),
    color: '#2f80ed',
    weight: 1,
    fillColor: '#5aa9ff',
    fillOpacity: 0.15
  }).addTo(map);

  userLocationMarker = createUserLocationMarker(lat, lon).addTo(map);

  const sourceLabel = source === 'address' ? 'Resolved Address Location' : 'Your Live Location';
  userLocationMarker.bindPopup(`<b>${sourceLabel}</b>`);
  map.flyTo([lat, lon], source === 'live' ? 15 : 14, { animate: true, duration: 0.8 });
  const accuracyLabel = Number.isFinite(Number(accuracyMeters)) && Number(accuracyMeters) > 0
    ? ` +/-${Math.round(Number(accuracyMeters))}m`
    : '';
  const prettySource = source === 'address' ? 'Address' : 'Live';
  setLocationSummary(`${prettySource} location captured: ${Number(lat).toFixed(5)}, ${Number(lon).toFixed(5)}${accuracyLabel}`);
}

function budgetRangeToAmount(range) {
  const normalized = (range || '').toLowerCase();
  if (normalized === 'low') return 400;
  if (normalized === 'high') return 1200;
  return 800;
}

function slugify(value) {
  return (value || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function getStoredUserKey() {
  return localStorage.getItem(USER_KEY_STORAGE) || '';
}

function persistUserKey(userKey) {
  if (userKey) {
    localStorage.setItem(USER_KEY_STORAGE, userKey);
  }
}

function ensureUserKey(profile) {
  const existing = getStoredUserKey();
  if (existing) {
    return existing;
  }
  const base = slugify(profile && profile.userName ? profile.userName : 'guest');
  const created = `${base || 'guest'}-${Math.random().toString(36).slice(2, 8)}`;
  persistUserKey(created);
  return created;
}

function getProfileFormData() {
  return {
    userName: q('userName'),
    ageGroup: q('ageGroup'),
    occupation: q('occupation'),
    defaultBudgetRange: q('defaultBudgetRange'),
    preferredCafeType: q('preferredCafeType'),
    preferredDistanceKm: q('preferredDistanceKm'),
    onboardingDiet: q('onboardingDiet'),
    usuallyVisitWith: q('usuallyVisitWith'),
    preferredSeating: q('preferredSeating'),
    musicPreference: q('musicPreference'),
    lightingPreference: q('lightingPreference')
  };
}

function getVisitContextFormData() {
  return {
    visitPurpose: q('visitPurpose'),
    visitBudgetRange: q('visitBudgetRange'),
    visitDistanceKm: q('visitDistanceKm'),
    visitTime: q('visitTime'),
    crowdTolerance: q('crowdTolerance')
  };
}

function hasOnboardingProfile(profile) {
  return !!(profile && profile.userName && profile.userName.trim().length > 0);
}

function saveProfileCache(profile) {
  localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(profile));
}

function loadProfileCache() {
  try {
    const raw = localStorage.getItem(PROFILE_CACHE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (err) {
    return null;
  }
}

function applyProfileToForm(profile) {
  if (!profile) return;
  Object.entries(profile).forEach(([key, value]) => {
    const node = document.getElementById(key);
    if (node && value !== undefined && value !== null) {
      node.value = String(value);
    }
  });
}

function applyVisitContextToForm(context) {
  if (!context) return;
  const mapping = {
    visitPurpose: context.purposeOfVisit,
    visitBudgetRange: context.currentBudgetRange,
    visitDistanceKm: context.travelDistanceKm,
    visitTime: context.timeOfVisit,
    crowdTolerance: context.crowdTolerance
  };
  Object.entries(mapping).forEach(([key, value]) => {
    const node = document.getElementById(key);
    if (node && value !== undefined && value !== null) {
      node.value = String(value);
    }
  });
}

function renderProfileSummary(profile) {
  if (!hasOnboardingProfile(profile)) {
    setProfileStatus('Complete onboarding once to unlock personalized recommendations.');
    return;
  }
  setProfileStatus(
    `Profile saved for ${profile.userName} | ${profile.preferredCafeType || 'General'} | ${profile.defaultBudgetRange || 'Medium'} budget`
  );
}

function profileFromApi(payload) {
  if (!payload || !payload.profile) return null;
  return {
    userName: payload.profile.name || '',
    ageGroup: payload.profile.ageGroup || '',
    occupation: payload.profile.occupation || '',
    defaultBudgetRange: payload.profile.defaultBudgetRange || '',
    preferredCafeType: payload.profile.preferredCafeType || '',
    preferredDistanceKm: String(payload.profile.preferredDistanceKm || 5),
    onboardingDiet: payload.profile.dietaryPreference || 'ANY',
    usuallyVisitWith: payload.socialPreference ? (payload.socialPreference.usuallyVisitWith || '') : '',
    preferredSeating: payload.socialPreference ? (payload.socialPreference.preferredSeating || '') : '',
    musicPreference: payload.ambiencePreference ? (payload.ambiencePreference.musicPreference || '') : '',
    lightingPreference: payload.ambiencePreference ? (payload.ambiencePreference.lightingPreference || '') : ''
  };
}

function toQuery(params) {
  return new URLSearchParams(params).toString();
}

async function fetchJson(url, options) {
  const res = await fetch(url, options);
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || 'Request failed');
  }
  return data;
}

async function hydrateSession() {
  const params = new URLSearchParams(window.location.search);
  if (params.get('guest') === '1') {
    setAuthBanner('Guest mode active. Login to save account-based history.');
    return null;
  }

  const stored = getStoredSession();
  if (!stored || !stored.session || !stored.session.sessionToken) {
    setAuthBanner('Guest mode active. Login to save account-based history.');
    return null;
  }

  try {
    const payload = await fetchJson(`/api/auth/me?${toQuery({ sessionToken: stored.session.sessionToken })}`);
    saveStoredSession(payload);
    persistUserKey(payload.user.userKey);
    if (payload.user.role === 'ADMIN') {
      window.location.href = '/admin.html';
      return null;
    }
    setAuthBanner(`Signed in as ${payload.user.displayName || payload.user.email} (${payload.user.role})`);
    return payload;
  } catch (err) {
    clearStoredSession();
    setAuthBanner('Guest mode active. Session expired or unavailable.');
    return null;
  }
}

async function loadOnboardingFromApi() {
  const userKey = getStoredUserKey();
  if (!userKey) {
    const cached = loadProfileCache();
    applyProfileToForm(cached);
    renderProfileSummary(cached);
    return cached;
  }

  try {
    const status = await fetchJson(`/api/onboarding/status?${toQuery({ userKey })}`);
    if (!status.databaseEnabled) {
      const cached = loadProfileCache();
      applyProfileToForm(cached);
      renderProfileSummary(cached);
      setStatus('SQLite onboarding storage is not enabled yet. Using local cache only.');
      return cached;
    }
    if (!status.profilePresent) {
      const cached = loadProfileCache();
      applyProfileToForm(cached);
      renderProfileSummary(cached);
      return cached;
    }

    const profilePayload = await fetchJson(`/api/onboarding/profile?${toQuery({ userKey })}`);
    const profile = profileFromApi(profilePayload);
    if (profile) {
      applyProfileToForm(profile);
      saveProfileCache(profile);
      renderProfileSummary(profile);
    }
    if (profilePayload.activeVisitContext) {
      applyVisitContextToForm(profilePayload.activeVisitContext);
    }
    return profile;
  } catch (err) {
    const cached = loadProfileCache();
    applyProfileToForm(cached);
    renderProfileSummary(cached);
    setStatus(`Onboarding sync fallback: ${err.message}`);
    return cached;
  }
}

async function saveProfile() {
  const profile = getProfileFormData();
  if (!hasOnboardingProfile(profile)) {
    setStatus('Please provide at least your name in onboarding profile.');
    return null;
  }

  const userKey = ensureUserKey(profile);
  saveProfileCache(profile);

  try {
    const payload = await fetchJson(`/api/onboarding/profile?${toQuery({
      userKey,
      ...profile
    })}`, { method: 'POST' });
    const savedProfile = profileFromApi(payload) || profile;
    applyProfileToForm(savedProfile);
    saveProfileCache(savedProfile);
    renderProfileSummary(savedProfile);
    setStatus('Onboarding profile saved to database.');
    return savedProfile;
  } catch (err) {
    renderProfileSummary(profile);
    setStatus(`Profile saved locally. Database sync pending: ${err.message}`);
    return profile;
  }
}

async function saveVisitContext() {
  const profile = getProfileFormData();
  const userKey = ensureUserKey(profile);
  const visitContext = getVisitContextFormData();

  try {
    const payload = await fetchJson(`/api/onboarding/context?${toQuery({
      userKey,
      userName: profile.userName || '',
      preferredDistanceKm: profile.preferredDistanceKm || '5',
      ...visitContext
    })}`, { method: 'POST' });
    if (payload.activeVisitContext) {
      applyVisitContextToForm(payload.activeVisitContext);
    }
    return payload;
  } catch (err) {
    setStatus(`Visit context not persisted to database: ${err.message}`);
    return null;
  }
}

function meter(label, value) {
  return `<div class="meta">${label}: <b>${value}/10</b></div>`;
}

function renderTagList(values, className) {
  return (values || []).map(value => `<span class="${className}">${value}</span>`).join('');
}

function labelForUseCase(key) {
  const labels = {
    hangout: 'Hangout',
    date: 'Date',
    meeting: 'Meeting',
    quickCoffee: 'Quick Coffee',
    privacy: 'Privacy',
    aesthetic: 'Aesthetic',
    work: 'Work'
  };
  return labels[key] || key;
}

function deriveCafeFit(suitability, workability) {
  const scores = {
    hangout: suitability.hangout,
    date: suitability.date,
    work: workability.overall,
    meeting: suitability.meeting,
    quickCoffee: suitability.quickCoffee
  };
  const ordered = Object.entries(scores).sort((a, b) => b[1] - a[1]);
  const best = ordered[0];
  const second = ordered[1];
  const worst = ordered[ordered.length - 1];
  const bestLabel = best && second && best[1] - second[1] <= 1 && best[1] >= 6 && second[1] >= 6
    ? `${labelForUseCase(best[0])} / ${labelForUseCase(second[0])}`
    : labelForUseCase(best[0]);
  return {
    best: bestLabel,
    worst: labelForUseCase(worst[0])
  };
}

async function voteSeat(cafeId, type) {
  const url = `/api/live/seat?cafeId=${encodeURIComponent(cafeId)}&status=${encodeURIComponent(type)}`;
  await fetch(url, { method: 'POST' });
  runSearch();
}

async function toggleTableShare(cafeId, available) {
  const url = `/api/live/table-share?cafeId=${encodeURIComponent(cafeId)}&available=${available}`;
  await fetch(url, { method: 'POST' });
  runSearch();
}

function renderResults(data) {
  const resultsDiv = document.getElementById('results');
  resultsDiv.innerHTML = '';
  const sourceLabel = (data.source || 'csv').toUpperCase();

  if (!data.results || data.results.length === 0) {
    resultsDiv.innerHTML = '<div class="card">No cafes found for this query.</div>';
    return;
  }

  data.results.forEach((r, idx) => {
    addMarker(r.id, r.latitude, r.longitude, `<b>${r.name}</b><br/>${r.distanceKm.toFixed(2)} km | ${r.acousticProfile}`);

    const card = document.createElement('div');
    card.className = 'card';
    const vibeTags = renderTagList(r.vibeTags, 'tag');
    const occasionTags = renderTagList(r.occasionTags, 'tag tag-accent');
    const milks = r.altMilks.join(', ') || 'N/A';
    const menuPreview = r.menuItems.slice(0, 4).join(', ');
    const seatTotal = r.liveStatus.easySeatVotes + r.liveStatus.standingVotes;
    const summary = r.insightSummary ? `<div class="summary">${r.insightSummary}</div>` : '';
    const fit = deriveCafeFit(r.suitability, r.workability);

    card.innerHTML = `
      <div class="card-top">
        <div>
          <div class="name">#${idx + 1} ${r.name}</div>
          <div class="meta">${r.distanceKm.toFixed(2)} km | Rs ${r.avgPrice.toFixed(0)} | ${r.rating.toFixed(1)}/5</div>
          <div class="meta">${r.address}</div>
          <div class="meta">Coords: ${r.latitude.toFixed(5)}, ${r.longitude.toFixed(5)}</div>
          <button class="btn alt" type="button" data-focus-cafe="${r.id}">Show On Map</button>
        </div>
        <div>
          <div class="source-badge">Source: ${sourceLabel}</div>
          <div class="meta">Match ${r.displayMatch.toFixed(0)}%</div>
        </div>
      </div>
      <div class="meta"><b>${r.profileTag || 'General Profile'}</b></div>
      <div class="meta"><b>${r.rankingReason || ''}</b></div>
      <div class="meta">${r.explanation || ''}</div>
      <div class="fit-strip">
        <span class="fit-pill">Best for: ${fit.best}</span>
        <span class="fit-pill fit-pill-muted">Weaker for: ${fit.worst}</span>
      </div>
      ${summary}

      <div class="tag-row">${occasionTags}</div>
      <div class="tag-row">${vibeTags}</div>
      <div class="grid">
        <div>
          ${meter('Workability', r.workability.overall)}
          ${meter('Wi-Fi', r.workability.wifi)}
          ${meter('Outlets', r.workability.outlets)}
          ${meter('Chairs', r.workability.chairs)}
        </div>
        <div>
          ${meter('Hangout', r.suitability.hangout)}
          ${meter('Date', r.suitability.date)}
          ${meter('Meeting', r.suitability.meeting)}
          ${meter('Quick Coffee', r.suitability.quickCoffee)}
          ${meter('Privacy', r.suitability.privacy)}
          ${meter('Aesthetic', r.suitability.aesthetic)}
        </div>
      </div>

      <div class="grid">
        <div>
          <div class="meta">Acoustic: <b>${r.acousticProfile}</b></div>
          <div class="meta">Sunlight: <b>${r.sunlightLabel}</b></div>
          <div class="meta">Roastery: <b>${r.roastery}</b></div>
        </div>
        <div>
          <div class="meta">Alt Milks: <b>${milks}</b></div>
          <div class="meta">Bike Rack: <b>${r.bikeRack ? 'Yes' : 'No'}</b></div>
          <div class="meta">Parking/Walkability: <b>${r.parkingScore}/10</b> , <b>${r.walkabilityScore}/10</b></div>
          <div class="meta">${r.independent ? 'Independent Cafe' : 'Chain Cafe'}</div>
        </div>
      </div>

      <div class="meta" style="margin-top:8px">Menu hits: ${menuPreview}</div>

      <div class="live">
        <div class="meta"><b>Real-Time Seat Map</b>: easy ${r.liveStatus.easySeatVotes} | standing ${r.liveStatus.standingVotes} ${seatTotal === 0 ? '(no signals yet)' : ''}</div>
        <div class="meta">Table sharing: <b>${r.liveStatus.tableShareAvailable ? 'Available' : 'Not signaled'}</b></div>
        <div class="actions">
          <button data-seat-easy="${r.id}">Found seat easily</button>
          <button data-seat-standing="${r.id}">Standing room only</button>
          <button data-share-on="${r.id}">I have extra chair</button>
          <button data-share-off="${r.id}">Close sharing</button>
        </div>
      </div>
    `;
    resultsDiv.appendChild(card);
  });

  resultsDiv.querySelectorAll('[data-seat-easy]').forEach(btn => {
    btn.addEventListener('click', () => voteSeat(btn.getAttribute('data-seat-easy'), 'easy'));
  });
  resultsDiv.querySelectorAll('[data-seat-standing]').forEach(btn => {
    btn.addEventListener('click', () => voteSeat(btn.getAttribute('data-seat-standing'), 'standing'));
  });
  resultsDiv.querySelectorAll('[data-share-on]').forEach(btn => {
    btn.addEventListener('click', () => toggleTableShare(btn.getAttribute('data-share-on'), true));
  });
  resultsDiv.querySelectorAll('[data-share-off]').forEach(btn => {
    btn.addEventListener('click', () => toggleTableShare(btn.getAttribute('data-share-off'), false));
  });
  resultsDiv.querySelectorAll('[data-focus-cafe]').forEach(btn => {
    btn.addEventListener('click', () => focusCafeOnMap(btn.getAttribute('data-focus-cafe')));
  });

  if (locationCaptureSource === 'live') {
    focusOnUserLocation(15);
  } else {
    fitMapToVisiblePoints(data.results);
  }
}

async function useAddressLocation() {
  const address = q('address');
  if (!address) {
    setStatus('Enter an address/place first.');
    return;
  }

  setStatus('Resolving address...');
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=${encodeURIComponent(address)}`;
    const res = await fetch(url, { headers: { Accept: 'application/json' } });
    const data = await res.json();
    if (!Array.isArray(data) || data.length === 0) {
      throw new Error('Address not found.');
    }
    setUserLocation(Number(data[0].lat), Number(data[0].lon), 30, 'address');
    await runSearch();
  } catch (err) {
    setStatus(`Address lookup failed: ${err.message}`);
  }
}

function handleLiveLocationError(err) {
  hasResolvedLocation = false;
  const cached = loadLocationCache();
  if (cached && Number.isFinite(Number(cached.lat)) && Number.isFinite(Number(cached.lon))) {
    setUserLocation(cached.lat, cached.lon, cached.accuracyMeters || 40, cached.source || 'live');
    setStatus(`Live location unavailable right now. Using last known ${cached.source || 'live'} location.`);
    return;
  }
  setLocationSummary('Location not captured yet.');
  setStatus(`Unable to get live location: ${err.message}`);
}

function useLiveLocation() {
  if (!navigator.geolocation) {
    setStatus('Geolocation is not supported by your browser.');
    return;
  }
  setStatus('Fetching your live location...');
  setLocationSummary('Waiting for browser location permission...');

  if (locationWatchId !== null) {
    navigator.geolocation.clearWatch(locationWatchId);
    locationWatchId = null;
  }

  let hasTriggeredSearchFromLive = false;
  locationWatchId = navigator.geolocation.watchPosition(
    async pos => {
      setUserLocation(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy, 'live');
      setStatus('Live location captured.');
      if (!hasTriggeredSearchFromLive) {
        hasTriggeredSearchFromLive = true;
        await runSearch();
      }
    },
    err => {
      if (locationWatchId !== null) {
        navigator.geolocation.clearWatch(locationWatchId);
        locationWatchId = null;
      }
      handleLiveLocationError(err);
    },
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 10000 }
  );
}

async function runSearch() {
  setStatus('Searching...');
  clearMap();

  let profile = getProfileFormData();
  if (!hasOnboardingProfile(profile)) {
    profile = loadProfileCache();
    if (profile) {
      applyProfileToForm(profile);
    }
  }
  if (!hasOnboardingProfile(profile)) {
    setStatus('Please complete onboarding profile before searching.');
    return;
  }

  const lat = Number(q('lat'));
  const lon = Number(q('lon'));
  const dynamicDistance = Number(q('visitDistanceKm'));
  const radius = Number.isFinite(dynamicDistance) && dynamicDistance > 0 ? dynamicDistance : Number(q('radius'));
  const dynamicBudgetRange = q('visitBudgetRange');
  const dynamicBudgetValue = budgetRangeToAmount(dynamicBudgetRange);
  document.getElementById('radius').value = String(radius);
  document.getElementById('budget').value = String(dynamicBudgetValue);

  if (!Number.isFinite(lat) || !Number.isFinite(lon) || !Number.isFinite(radius)) {
    setStatus('Location not available yet. Use live location or enter an address first.');
    return;
  }

  if (!hasResolvedLocation) {
    setStatus('Please allow live location or use an address before searching.');
    return;
  }

  await saveVisitContext();

  radiusCircle = L.circle([lat, lon], { radius: radius * 1000, color: '#8f3e27', fillOpacity: 0.08 }).addTo(map);

  const params = new URLSearchParams({
    lat: q('lat'),
    lon: q('lon'),
    radius: String(radius),
    budget: String(dynamicBudgetValue),
    source: 'csv',
    cuisines: q('cuisines'),
    vibes: q('vibes'),
    acoustic: q('acoustic'),
    menuQuery: q('menuQuery'),
    diet: q('diet'),
    k: q('k'),
    indieOnly: document.getElementById('indieOnly').checked ? 'true' : 'false',
    userName: profile.userName || '',
    ageGroup: profile.ageGroup || '',
    occupation: profile.occupation || '',
    defaultBudgetRange: profile.defaultBudgetRange || '',
    preferredCafeType: profile.preferredCafeType || '',
    preferredDistanceKm: profile.preferredDistanceKm || '5',
    onboardingDiet: profile.onboardingDiet || 'ANY',
    usuallyVisitWith: profile.usuallyVisitWith || '',
    preferredSeating: profile.preferredSeating || '',
    musicPreference: profile.musicPreference || '',
    lightingPreference: profile.lightingPreference || '',
    visitPurpose: q('visitPurpose'),
    visitBudgetRange: dynamicBudgetRange,
    visitDistanceKm: q('visitDistanceKm'),
    visitTime: q('visitTime'),
    crowdTolerance: q('crowdTolerance')
  });

  const activeSession = currentSession && currentSession.session ? currentSession.session : null;
  if (activeSession && activeSession.sessionToken) {
    params.set('sessionToken', activeSession.sessionToken);
  }
  params.set('userKey', ensureUserKey(profile));
  params.set('locationSource', locationCaptureSource || 'unknown');

  try {
    const res = await fetch(`/api/recommend?${params.toString()}`);
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error || 'Request failed');
    }
    renderResults(data);
    const usedSource = (data.source || 'csv').toUpperCase();
    setStatus(`Returned ${data.count} cafes from ${usedSource} pipeline. Onboarding and visit context were applied.`);
  } catch (err) {
    setStatus(`Error: ${err.message}`);
  }
}

document.getElementById('searchBtn').addEventListener('click', runSearch);
document.getElementById('liveLocationBtn').addEventListener('click', useLiveLocation);
document.getElementById('recenterLocationBtn').addEventListener('click', () => focusOnUserLocation(15));
document.getElementById('logoutBtn').addEventListener('click', async () => {
  try {
    const stored = getStoredSession();
    if (stored && stored.session && stored.session.sessionToken) {
      await fetchJson(`/api/auth/logout?${toQuery({ sessionToken: stored.session.sessionToken })}`, { method: 'POST' });
    }
  } catch (err) {
    // ignore logout errors and clear local session
  }
  clearStoredSession();
  window.location.href = '/landing.html';
});
document.getElementById('locateAddressBtn').addEventListener('click', useAddressLocation);
document.getElementById('saveProfileBtn').addEventListener('click', async () => {
  await saveProfile();
});

(async function init() {
  await hydrateSession();
  await loadOnboardingFromApi();
  const cachedLocation = loadLocationCache();
  if (cachedLocation && Number.isFinite(Number(cachedLocation.lat)) && Number.isFinite(Number(cachedLocation.lon))) {
    setUserLocation(
      cachedLocation.lat,
      cachedLocation.lon,
      cachedLocation.accuracyMeters || 40,
      cachedLocation.source || 'live'
    );
    setStatus('Restored last known location while refreshing live tracking.');
  } else {
    setLocationSummary('Location not captured yet.');
  }
  useLiveLocation();
})();

