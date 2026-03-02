const map = L.map('map').setView([18.5204, 73.8567], 12);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap contributors'
}).addTo(map);

let markers = [];
const markerCluster = L.markerClusterGroup({ spiderfyOnMaxZoom: true, showCoverageOnHover: false });
map.addLayer(markerCluster);
let radiusCircle = null;

function clearMap() {
  markers.forEach(m => markerCluster.removeLayer(m));
  markers = [];
  if (radiusCircle) {
    map.removeLayer(radiusCircle);
    radiusCircle = null;
  }
}

function addMarker(lat, lon, popup) {
  const marker = L.marker([lat, lon]).bindPopup(popup);
  markerCluster.addLayer(marker);
  markers.push(marker);
}

function q(id) {
  return document.getElementById(id).value.trim();
}

function renderResults(data) {
  const resultsDiv = document.getElementById('results');
  resultsDiv.innerHTML = '';

  if (!data.results || data.results.length === 0) {
    resultsDiv.innerHTML = '<div class="card">No cafes found for this query.</div>';
    return;
  }

  data.results.forEach((r, idx) => {
    const card = document.createElement('div');
    card.className = 'card';
    card.innerHTML = `
      <div class="rank">#${idx + 1} ${r.name}</div>
      <div class="meta">${r.distanceKm.toFixed(2)} km | ?${r.avgPrice.toFixed(0)} | ${r.rating.toFixed(1)}/5</div>
      <div class="meta">Cuisines: ${r.cuisines.join(', ')}</div>
      <div class="meta">Match: ${r.displayMatch.toFixed(0)}%</div>
      <div class="meta">${r.address}</div>
      <div class="meta">${r.contact}</div>
    `;
    resultsDiv.appendChild(card);

    addMarker(
      r.latitude,
      r.longitude,
      `<b>${r.name}</b><br/>Distance: ${r.distanceKm.toFixed(2)} km<br/>Rating: ${r.rating.toFixed(1)}<br/>Price: ?${r.avgPrice.toFixed(0)}`
    );
  });
}

async function runSearch() {
  const status = document.getElementById('status');
  status.textContent = 'Searching...';

  const lat = Number(q('lat'));
  const lon = Number(q('lon'));
  const radius = Number(q('radius'));

  const params = new URLSearchParams({
    lat: q('lat'),
    lon: q('lon'),
    radius: q('radius'),
    budget: q('budget'),
    cuisines: q('cuisines'),
    diet: q('diet'),
    k: q('k'),
    w1: q('w1'),
    w2: q('w2'),
    w3: q('w3'),
    w4: q('w4')
  });

  try {
    clearMap();
    addMarker(lat, lon, '<b>Your Location</b>');
    radiusCircle = L.circle([lat, lon], { radius: radius * 1000, color: '#b04a2f', fillOpacity: 0.08 }).addTo(map);
    map.setView([lat, lon], 13);

    const res = await fetch(`/api/recommend?${params.toString()}`);
    const data = await res.json();

    if (!res.ok) {
      throw new Error(data.error || 'Request failed');
    }

    renderResults(data);
    status.textContent = `Returned ${data.count} cafes. If points overlap, zoom in or click clusters.`;
  } catch (err) {
    status.textContent = `Error: ${err.message}`;
  }
}

function setUserLocation(lat, lon) {
  document.getElementById('lat').value = lat.toFixed(6);
  document.getElementById('lon').value = lon.toFixed(6);
  map.setView([lat, lon], 13);
}

function useLiveLocation() {
  const status = document.getElementById('status');
  if (!navigator.geolocation) {
    status.textContent = 'Geolocation is not supported by your browser.';
    return;
  }
  status.textContent = 'Fetching your live location...';
  navigator.geolocation.getCurrentPosition(
    pos => {
      const lat = pos.coords.latitude;
      const lon = pos.coords.longitude;
      setUserLocation(lat, lon);
      status.textContent = 'Live location set. Running search...';
      runSearch();
    },
    err => {
      status.textContent = `Unable to get live location: ${err.message}`;
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 }
  );
}

async function useAddressLocation() {
  const status = document.getElementById('status');
  const address = q('address');
  if (!address) {
    status.textContent = 'Enter an address/place first.';
    return;
  }

  status.textContent = 'Resolving address...';
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=${encodeURIComponent(address)}`;
    const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
    const data = await res.json();
    if (!Array.isArray(data) || data.length === 0) {
      throw new Error('Address not found.');
    }
    const lat = Number(data[0].lat);
    const lon = Number(data[0].lon);
    setUserLocation(lat, lon);
    status.textContent = 'Address location set. Running search...';
    runSearch();
  } catch (err) {
    status.textContent = `Address lookup failed: ${err.message}`;
  }
}

document.getElementById('searchBtn').addEventListener('click', runSearch);
document.getElementById('liveLocationBtn').addEventListener('click', useLiveLocation);
document.getElementById('locateAddressBtn').addEventListener('click', useAddressLocation);
useLiveLocation();
