const map = L.map('map').setView([18.5204, 73.8567], 12);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap contributors'
}).addTo(map);

let markers = [];
const markerCluster = L.markerClusterGroup({ spiderfyOnMaxZoom: true, showCoverageOnHover: false });
map.addLayer(markerCluster);
let radiusCircle = null;
const PROFILE_KEY = 'cafe_user_profile_v1';

function q(id) {
  const node = document.getElementById(id);
  if (!node) {
    return '';
  }
  return node.value.trim();
}

function clearMap() {
  markers.forEach(m => markerCluster.removeLayer(m));
  markers = [];
  if (radiusCircle) {
    map.removeLayer(radiusCircle);
    radiusCircle = null;
  }
}

function addMarker(lat, lon, popupHtml) {
  const marker = L.marker([lat, lon]).bindPopup(popupHtml);
  markerCluster.addLayer(marker);
  markers.push(marker);
}

function setStatus(message) {
  document.getElementById('status').textContent = message;
}

function setUserLocation(lat, lon) {
  document.getElementById('lat').value = Number(lat).toFixed(6);
  document.getElementById('lon').value = Number(lon).toFixed(6);
  map.setView([lat, lon], 13);
}

function budgetRangeToAmount(range) {
  const normalized = (range || '').toLowerCase();
  if (normalized === 'low') return 400;
  if (normalized === 'high') return 1200;
  return 800;
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

function hasOnboardingProfile(profile) {
  return !!(profile && profile.userName && profile.userName.trim().length > 0);
}

function renderProfileSummary(profile) {
  const summary = document.getElementById('profileSummary');
  if (!summary) return;
  if (!hasOnboardingProfile(profile)) {
    summary.textContent = 'Complete onboarding once to unlock personalized recommendations.';
    return;
  }
  summary.textContent = `Profile saved for ${profile.userName} (${profile.ageGroup || 'N/A'}) | ${profile.preferredCafeType || 'General'} | ${profile.defaultBudgetRange || 'Medium'} budget`;
}

function applyProfileToForm(profile) {
  if (!profile) return;
  Object.entries(profile).forEach(([key, value]) => {
    const node = document.getElementById(key);
    if (node) {
      node.value = value;
    }
  });
}

function loadSavedProfile() {
  try {
    const raw = localStorage.getItem(PROFILE_KEY);
    if (!raw) {
      renderProfileSummary(null);
      return null;
    }
    const parsed = JSON.parse(raw);
    applyProfileToForm(parsed);
    renderProfileSummary(parsed);
    return parsed;
  } catch (err) {
    renderProfileSummary(null);
    return null;
  }
}

function saveProfile() {
  const profile = getProfileFormData();
  if (!hasOnboardingProfile(profile)) {
    setStatus('Please provide at least your name in onboarding profile.');
    return null;
  }
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
  renderProfileSummary(profile);
  setStatus('Onboarding profile saved.');
  return profile;
}

function meter(label, value) {
  return `<div class="meta">${label}: <b>${value}/10</b></div>`;
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
  const sourceLabel = (data.source || q('source') || 'csv').toUpperCase();

  if (!data.results || data.results.length === 0) {
    resultsDiv.innerHTML = '<div class="card">No cafes found for this query.</div>';
    return;
  }

  data.results.forEach((r, idx) => {
    addMarker(r.latitude, r.longitude, `<b>${r.name}</b><br/>${r.distanceKm.toFixed(2)} km | ${r.acousticProfile}`);

    const card = document.createElement('div');
    card.className = 'card';
    const tags = r.vibeTags.map(t => `<span class="tag">${t}</span>`).join('');
    const milks = r.altMilks.join(', ') || 'N/A';
    const menuPreview = r.menuItems.slice(0, 4).join(', ');
    const seatTotal = r.liveStatus.easySeatVotes + r.liveStatus.standingVotes;

    card.innerHTML = `
      <div class="card-top">
        <div>
          <div class="name">#${idx + 1} ${r.name}</div>
          <div class="meta">${r.distanceKm.toFixed(2)} km | Rs ${r.avgPrice.toFixed(0)} | ${r.rating.toFixed(1)}/5</div>
          <div class="meta">${r.address}</div>
        </div>
        <div>
          <div class="source-badge">Source: ${sourceLabel}</div>
          <div class="meta">Match ${r.displayMatch.toFixed(0)}%</div>
        </div>
      </div>
      <div class="meta"><b>${r.profileTag || 'General Profile'}</b></div>
      <div class="meta">${r.explanation || ''}</div>

      <div class="tag-row">${tags}</div>
      <div class="grid">
        <div>
          ${meter('Workability', r.workability.overall)}
          ${meter('Wi-Fi', r.workability.wifi)}
          ${meter('Outlets', r.workability.outlets)}
          ${meter('Chairs', r.workability.chairs)}
        </div>
        <div>
          <div class="meta">Acoustic: <b>${r.acousticProfile}</b></div>
          <div class="meta">Sunlight: <b>${r.sunlightLabel}</b></div>
          <div class="meta">Roastery: <b>${r.roastery}</b></div>
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
    setUserLocation(Number(data[0].lat), Number(data[0].lon));
    await runSearch();
  } catch (err) {
    setStatus(`Address lookup failed: ${err.message}`);
  }
}

function useLiveLocation() {
  if (!navigator.geolocation) {
    setStatus('Geolocation is not supported by your browser.');
    return;
  }
  setStatus('Fetching your live location...');
  navigator.geolocation.getCurrentPosition(
    async pos => {
      setUserLocation(pos.coords.latitude, pos.coords.longitude);
      await runSearch();
    },
    err => setStatus(`Unable to get live location: ${err.message}`),
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 }
  );
}

async function runSearch() {
  setStatus('Searching...');
  clearMap();

  let profile = loadSavedProfile();
  if (!hasOnboardingProfile(profile)) {
    profile = saveProfile();
    if (!hasOnboardingProfile(profile)) {
      setStatus('Please complete onboarding profile before searching.');
      return;
    }
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
    setStatus('Invalid coordinates or radius.');
    return;
  }

  addMarker(lat, lon, '<b>Your Location</b>');
  radiusCircle = L.circle([lat, lon], { radius: radius * 1000, color: '#8f3e27', fillOpacity: 0.08 }).addTo(map);

  const params = new URLSearchParams({
    lat: q('lat'),
    lon: q('lon'),
    radius: String(radius),
    budget: String(dynamicBudgetValue),
    source: q('source'),
    cuisines: q('cuisines'),
    vibes: q('vibes'),
    acoustic: q('acoustic'),
    menuQuery: q('menuQuery'),
    diet: q('diet'),
    k: q('k'),
    indieOnly: document.getElementById('indieOnly').checked ? 'true' : 'false',
    w1: q('w1'),
    w2: q('w2'),
    w3: q('w3'),
    w4: q('w4'),
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

  try {
    const res = await fetch(`/api/recommend?${params.toString()}`);
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error || 'Request failed');
    }
    renderResults(data);
    const usedSource = (data.source || q('source')).toUpperCase();
    setStatus(`Returned ${data.count} cafes from ${usedSource} pipeline. Clusters indicate overlapping markers.`);
  } catch (err) {
    setStatus(`Error: ${err.message}`);
  }
}

document.getElementById('searchBtn').addEventListener('click', runSearch);
document.getElementById('liveLocationBtn').addEventListener('click', useLiveLocation);
document.getElementById('locateAddressBtn').addEventListener('click', useAddressLocation);
document.getElementById('saveProfileBtn').addEventListener('click', saveProfile);
loadSavedProfile();
useLiveLocation();
