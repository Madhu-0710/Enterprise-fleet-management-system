/**
 * Enterprise Fleet Management System - Frontend Application Logic
 * Integrates directly with Java SE Backend APIs
 */

let fleetVehicles = [];
let selectedVehicle = null;
let currentTripType = 'one-way';
let activeUserBookingId = null;
let pollIntervalTimer = null;

// Icons mapping for vehicles
const vehicleIcons = {
    'v1': '🚚',
    'v2': '🛻',
    'v3': '🚛',
    'v4': '🚛',
    'v5': '🚛',
    'v6': '❄️',
    'v7': '🏗️'
};

document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

async function initApp() {
    updateHostBadge();
    setupEventListeners();
    await fetchFleetData();
    await fetchBookings();
    triggerFareRecalculation();
    initHeroQuickCalculator();
}

function updateHostBadge() {
    const badge = document.getElementById('hostUrlBadge');
    if (badge) {
        const port = window.location.port ? `:${window.location.port}` : '';
        const host = window.location.hostname || 'localhost';
        badge.textContent = `🌐 Server: http://${host}${port}`;
    }
}

// -----------------------------------------------------------------------------
// 1. Fetch Fleet Data from Java Backend API
// -----------------------------------------------------------------------------
async function fetchFleetData() {
    const container = document.getElementById('fleetGridContainer');
    const heroSelect = document.getElementById('heroVehicleSelect');
    const calcSelect = document.getElementById('calcVehicleSelect');

    try {
        const response = await fetch('/api/fleet');
        if (!response.ok) throw new Error('Network response failed');
        const data = await response.json();

        fleetVehicles = data.vehicles || [];

        // Update hero metrics
        const heroTotal = document.getElementById('heroTotalVehicles');
        if (heroTotal) heroTotal.textContent = data.activeFleetCount;

        // Render options in select drop-downs
        if (heroSelect) heroSelect.innerHTML = '';
        if (calcSelect) calcSelect.innerHTML = '';

        fleetVehicles.forEach((v, index) => {
            const optText = `${v.name} (${v.loadCapacity}) — ₹${v.ratePerKm}/km`;
            
            const opt1 = new Option(optText, v.id);
            if (heroSelect) heroSelect.add(opt1);

            const opt2 = new Option(optText, v.id);
            if (calcSelect) calcSelect.add(opt2);
        });

        // Set default selection to v4 (Heavy Truck) or first item
        const defaultVehicle = fleetVehicles.find(v => v.id === 'v4') || fleetVehicles[0];
        if (defaultVehicle) {
            selectedVehicle = defaultVehicle;
            if (calcSelect) calcSelect.value = defaultVehicle.id;
            if (heroSelect) heroSelect.value = defaultVehicle.id;
        }

        // Render Vehicle Catalog Grid
        renderVehicleCatalog(fleetVehicles);

    } catch (err) {
        console.error('Failed to load fleet data:', err);
        showToast('Using offline fleet cache. Backend connecting...', 'warning');
        renderFallbackVehicles();
    }
}

function renderVehicleCatalog(vehiclesList) {
    const container = document.getElementById('fleetGridContainer');
    if (!container) return;

    if (!vehiclesList || vehiclesList.length === 0) {
        container.innerHTML = '<div class="loading-spinner-wrap"><p>No vehicles registered in fleet.</p></div>';
        return;
    }

    container.innerHTML = vehiclesList.map(v => {
        const icon = vehicleIcons[v.id] || '🚛';
        return `
            <div class="vehicle-card gradient-border-hover" id="card-${v.id}">
                <div class="vehicle-card-top">
                    <div class="card-badge-row">
                        <span class="vehicle-tag-badge">${escapeHtml(v.badge || 'Commercial')}</span>
                        <span class="vehicle-available-pill">
                            <span class="pulse-dot"></span> ${v.availableCount} Available
                        </span>
                    </div>
                    <div class="vehicle-icon-large">${icon}</div>
                    <h3 class="vehicle-title">${escapeHtml(v.name)}</h3>
                    <p class="vehicle-category">${escapeHtml(v.category)}</p>
                </div>

                <div class="vehicle-card-body">
                    <!-- Prominent Rupees for 1 KM & Load capacity as requested -->
                    <div class="vehicle-specs-grid">
                        <div class="spec-box highlight-rate">
                            <span class="spec-title">Rupees For 1 KM</span>
                            <span class="spec-rate-val">₹${v.ratePerKm} <small style="font-size:0.75rem; color:#94a3b8;">/km</small></span>
                        </div>
                        <div class="spec-box highlight-load">
                            <span class="spec-title">Max Load Capacity</span>
                            <span class="spec-load-val">${escapeHtml(v.loadCapacity)}</span>
                        </div>
                    </div>

                    <p class="vehicle-desc">${escapeHtml(v.description)}</p>

                    <div class="vehicle-card-footer">
                        <button type="button" class="btn-select-fleet" onclick="selectVehicleForBooking('${v.id}')">
                            Select For Fare Calculation &rarr;
                        </button>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function renderFallbackVehicles() {
    const fallback = [
        { id: 'v1', name: 'Tata Ace Gold', category: 'LCV', loadCapacity: '750 kg', ratePerKm: 22, availableCount: 14, description: 'Intra-city logistics & fast parcel transit' },
        { id: 'v2', name: 'Mahindra Bolero Maxi', category: 'Pickup', loadCapacity: '1,500 kg', ratePerKm: 32, availableCount: 18, description: 'Regional delivery & farm produce' },
        { id: 'v3', name: 'Eicher Pro 14-Foot', category: 'ICV', loadCapacity: '5,000 kg', ratePerKm: 48, availableCount: 12, description: 'Covered container for electronics & parcels' },
        { id: 'v4', name: 'Ashok Leyland 20-Foot', category: 'MHCV', loadCapacity: '12,000 kg', ratePerKm: 75, availableCount: 8, description: 'Interstate logistics & industrial hardware' }
    ];
    renderVehicleCatalog(fallback);
}

// -----------------------------------------------------------------------------
// 2. Select Vehicle & Sync UI
// -----------------------------------------------------------------------------
function selectVehicleForBooking(vehicleId) {
    const v = fleetVehicles.find(item => item.id === vehicleId);
    if (!v) return;

    selectedVehicle = v;

    const calcSelect = document.getElementById('calcVehicleSelect');
    if (calcSelect) {
        calcSelect.value = vehicleId;
    }

    triggerFareRecalculation();

    const calcSection = document.getElementById('calculator');
    if (calcSection) {
        calcSection.scrollIntoView({ behavior: 'smooth' });
    }

    showToast(`Selected ${v.name} (₹${v.ratePerKm}/km)`, 'success');
}

// -----------------------------------------------------------------------------
// 3. Dynamic Trip Fare Calculation (One-way vs Round-trip)
// -----------------------------------------------------------------------------
function setupEventListeners() {
    const tripRadios = document.querySelectorAll('input[name="tripType"]');
    tripRadios.forEach(radio => {
        radio.addEventListener('change', (e) => {
            currentTripType = e.target.value;
            triggerFareRecalculation();
        });
    });

    const heroOneWay = document.getElementById('heroTripOneWay');
    const heroRound = document.getElementById('heroTripRound');
    if (heroOneWay && heroRound) {
        heroOneWay.addEventListener('click', () => {
            heroOneWay.classList.add('active');
            heroRound.classList.remove('active');
            updateHeroCalculation();
        });
        heroRound.addEventListener('click', () => {
            heroRound.classList.add('active');
            heroOneWay.classList.remove('active');
            updateHeroCalculation();
        });
    }

    const heroVehicleSelect = document.getElementById('heroVehicleSelect');
    const heroKmInput = document.getElementById('heroKmInput');
    if (heroVehicleSelect) heroVehicleSelect.addEventListener('change', updateHeroCalculation);
    if (heroKmInput) heroKmInput.addEventListener('input', updateHeroCalculation);
}

function syncKmSlider(val) {
    const range = document.getElementById('calcKmRange');
    const label = document.getElementById('kmSliderDisplay');
    if (range) range.value = val;
    if (label) label.textContent = `${val} KM`;
}

function syncKmInput(val) {
    const num = document.getElementById('calcKmInput');
    const label = document.getElementById('kmSliderDisplay');
    if (num) num.value = val;
    if (label) label.textContent = `${val} KM`;
}

function setQuickRoute(src, dst, kms) {
    const srcInput = document.getElementById('calcSourceInput');
    const dstInput = document.getElementById('calcDestInput');
    const kmInput = document.getElementById('calcKmInput');

    if (srcInput) srcInput.value = src;
    if (dstInput) dstInput.value = dst;
    if (kmInput) {
        kmInput.value = kms;
        syncKmSlider(kms);
    }
    triggerFareRecalculation();
}

async function triggerFareRecalculation() {
    const calcSelect = document.getElementById('calcVehicleSelect');
    const kmInput = document.getElementById('calcKmInput');
    const tripChecked = document.querySelector('input[name="tripType"]:checked');

    if (!calcSelect || !kmInput) return;

    const vId = calcSelect.value || (selectedVehicle ? selectedVehicle.id : 'v1');
    const kms = parseFloat(kmInput.value) || 50;
    const tripType = tripChecked ? tripChecked.value : 'one-way';

    const v = fleetVehicles.find(x => x.id === vId) || selectedVehicle || {
        id: 'v4', name: 'Ashok Leyland 1615 HE', loadCapacity: '12.0 Tons', ratePerKm: 75.0, baseFare: 2800.0
    };
    selectedVehicle = v;

    try {
        const resp = await fetch('/api/calculate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ vehicleId: v.id, kms: kms, tripType: tripType })
        });

        if (resp.ok) {
            const result = await resp.json();
            updateBreakdownUI(result, v);
            updatePreviewBookingSummary(result, v);
            return;
        }
    } catch (e) {
        console.warn('API calculate call fallback to client computation:', e);
    }

    const billableKms = (tripType === 'round-trip') ? kms * 2 : kms;
    const baseFare = (tripType === 'round-trip') ? Math.round(v.baseFare * 1.5) : v.baseFare;
    const distanceCost = (tripType === 'round-trip') 
        ? Math.round(billableKms * v.ratePerKm * 0.92) 
        : Math.round(kms * v.ratePerKm);
    const tolls = billableKms > 40 ? Math.round(billableKms * 1.5) : 0;
    const subtotal = baseFare + distanceCost + tolls;
    const gst = Math.round(subtotal * 0.05);
    const total = subtotal + gst;

    const fallbackResult = {
        kms, tripType, billableKms, ratePerKm: v.ratePerKm,
        baseFare, distanceCost, tollTaxes: tolls, gst, total
    };
    updateBreakdownUI(fallbackResult, v);
    updatePreviewBookingSummary(fallbackResult, v);
}

function updateBreakdownUI(res, v) {
    const avatar = document.getElementById('calcVehicleAvatar');
    const nameEl = document.getElementById('calcVehicleName');
    const rateTag = document.getElementById('calcVehicleRateTag');
    const loadTag = document.getElementById('calcVehicleLoadTag');

    if (avatar) avatar.textContent = vehicleIcons[v.id] || '🚛';
    if (nameEl) nameEl.textContent = v.name;
    if (rateTag) rateTag.textContent = `Rate: ₹${v.ratePerKm} / KM`;
    if (loadTag) loadTag.textContent = `Load: ${v.loadCapacity}`;

    const dispBaseFare = document.getElementById('dispBaseFare');
    const dispBillableKm = document.getElementById('dispBillableKm');
    const dispDistanceCost = document.getElementById('dispDistanceCost');
    const dispToll = document.getElementById('dispToll');
    const dispGst = document.getElementById('dispGst');
    const dispGrandTotal = document.getElementById('dispGrandTotal');

    const tripLabel = res.tripType === 'round-trip' ? '(Round-Trip 🔁)' : '(One-Way)';

    if (dispBaseFare) dispBaseFare.textContent = formatRupees(res.baseFare);
    if (dispBillableKm) dispBillableKm.textContent = `${res.billableKms} KM ${tripLabel}`;
    if (dispDistanceCost) dispDistanceCost.textContent = formatRupees(res.distanceCost);
    if (dispToll) dispToll.textContent = formatRupees(res.tollTaxes);
    if (dispGst) dispGst.textContent = formatRupees(res.gst);
    if (dispGrandTotal) dispGrandTotal.textContent = formatRupees(res.total);
}

function updatePreviewBookingSummary(res, v) {
    const srcInput = document.getElementById('calcSourceInput');
    const dstInput = document.getElementById('calcDestInput');

    const src = srcInput ? srcInput.value : 'Origin Hub';
    const dst = dstInput ? dstInput.value : 'Destination Hub';

    const pVehicle = document.getElementById('previewVehicleTitle');
    const pSrc = document.getElementById('previewSource');
    const pDst = document.getElementById('previewDest');
    const pKm = document.getElementById('previewKm');
    const pAmount = document.getElementById('previewAmount');

    const tripSuffix = res.tripType === 'round-trip' ? '🔁 Round-Trip' : 'One-Way';

    if (pVehicle) pVehicle.textContent = `${v.name} (${v.loadCapacity})`;
    if (pSrc) pSrc.textContent = src;
    if (pDst) pDst.textContent = dst;
    if (pKm) pKm.textContent = `${res.billableKms} KM (${tripSuffix})`;
    if (pAmount) pAmount.textContent = formatRupees(res.total);
}

// Hero Quick Calculator
function initHeroQuickCalculator() {
    updateHeroCalculation();
}

function updateHeroCalculation() {
    const select = document.getElementById('heroVehicleSelect');
    const kmInput = document.getElementById('heroKmInput');
    const roundBtn = document.getElementById('heroTripRound');
    const isRound = roundBtn && roundBtn.classList.contains('active');

    if (!select || !kmInput) return;

    const vId = select.value;
    const kms = parseFloat(kmInput.value) || 120;
    const v = fleetVehicles.find(x => x.id === vId) || fleetVehicles[0];
    if (!v) return;

    const billable = isRound ? kms * 2 : kms;
    const baseFare = isRound ? v.baseFare * 1.5 : v.baseFare;
    const distCost = isRound ? (billable * v.ratePerKm * 0.92) : (kms * v.ratePerKm);
    const tolls = billable > 40 ? billable * 1.5 : 0;
    const subtotal = baseFare + distCost + tolls;
    const total = Math.round(subtotal * 1.05);

    const fareEl = document.getElementById('heroCalculatedFare');
    const rateEl = document.getElementById('heroDisplayRate');
    const loadEl = document.getElementById('heroDisplayLoad');
    const billEl = document.getElementById('heroDisplayBillable');

    if (fareEl) fareEl.textContent = formatNumber(total);
    if (rateEl) rateEl.textContent = `₹${v.ratePerKm}/km`;
    if (loadEl) loadEl.textContent = v.loadCapacity;
    if (billEl) billEl.textContent = `${billable} KM ${isRound ? '(Round)' : ''}`;
}

function scrollToBookingWithHeroValues() {
    const heroSel = document.getElementById('heroVehicleSelect');
    const heroKm = document.getElementById('heroKmInput');
    const roundBtn = document.getElementById('heroTripRound');
    const isRound = roundBtn && roundBtn.classList.contains('active');

    if (heroSel) {
        const calcSel = document.getElementById('calcVehicleSelect');
        if (calcSel) calcSel.value = heroSel.value;
    }
    if (heroKm) {
        const calcKm = document.getElementById('calcKmInput');
        if (calcKm) {
            calcKm.value = heroKm.value;
            syncKmSlider(heroKm.value);
        }
    }

    const radios = document.querySelectorAll('input[name="tripType"]');
    radios.forEach(r => {
        if (isRound && r.value === 'round-trip') r.checked = true;
        if (!isRound && r.value === 'one-way') r.checked = true;
    });

    triggerFareRecalculation();

    const bookSec = document.getElementById('booking');
    if (bookSec) bookSec.scrollIntoView({ behavior: 'smooth' });
}

function prefillBookingFormAndScroll() {
    triggerFareRecalculation();
    const bookSec = document.getElementById('booking');
    if (bookSec) bookSec.scrollIntoView({ behavior: 'smooth' });
}

// -----------------------------------------------------------------------------
// 4. Booking Submission & Approval Pipeline
// "till then it is in processing state...after approved, they will get the vehicle"
// -----------------------------------------------------------------------------
async function handleBookingSubmission(e) {
    e.preventDefault();

    const submitBtn = document.getElementById('submitBookingBtn');
    const spinner = document.getElementById('submitSpinner');
    const compName = document.getElementById('bookCompanyName').value.trim();
    const contact = document.getElementById('bookContactName').value.trim();
    const phone = document.getElementById('bookPhone').value.trim();
    const cargo = document.getElementById('bookCargo').value.trim();
    const weight = document.getElementById('bookWeight').value.trim();

    const src = document.getElementById('calcSourceInput').value.trim();
    const dst = document.getElementById('calcDestInput').value.trim();
    const kms = parseFloat(document.getElementById('calcKmInput').value) || 50;
    const tripRadio = document.querySelector('input[name="tripType"]:checked');
    const tripType = tripRadio ? tripRadio.value : 'one-way';
    const vehicleId = selectedVehicle ? selectedVehicle.id : 'v4';

    if (!compName || !contact || !phone || !cargo) {
        showToast('Please fill out all required requisition fields.', 'warning');
        return;
    }

    const payload = {
        companyName: compName,
        contactName: contact,
        contactPhone: phone,
        vehicleId: vehicleId,
        source: src,
        destination: dst,
        kms: kms,
        tripType: tripType,
        cargoDescription: `${cargo}${weight ? ' (Weight: ' + weight + ')' : ''}`
    };

    try {
        if (submitBtn) submitBtn.disabled = true;
        if (spinner) spinner.style.display = 'inline-block';

        const response = await fetch('/api/bookings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) throw new Error('Submission failed');
        const booking = await response.json();

        activeUserBookingId = booking.id;
        showBookingTrackerCard(booking);
        showToast(`Requisition ${booking.id} logged! Status: PROCESSING`, 'warning');

        await fetchBookings();
        startLivePolling(booking.id);

    } catch (err) {
        console.error('Requisition error:', err);
        showToast('Server communication failed. Please retry.', 'warning');
    } finally {
        if (submitBtn) submitBtn.disabled = false;
        if (spinner) spinner.style.display = 'none';
    }
}

function showBookingTrackerCard(booking) {
    const tracker = document.getElementById('userBookingTracker');
    if (!tracker) return;

    tracker.style.display = 'block';

    const idEl = document.getElementById('liveBookingId');
    if (idEl) idEl.textContent = `Requisition #${booking.id}`;

    updateTrackerStatus(booking);
    tracker.scrollIntoView({ behavior: 'smooth' });
}

function updateTrackerStatus(booking) {
    const badge = document.getElementById('liveBookingStatusBadge');
    const step1 = document.getElementById('step1');
    const step2 = document.getElementById('step2');
    const step3 = document.getElementById('step3');
    const conn1 = document.getElementById('conn1');
    const conn2 = document.getElementById('conn2');

    const procNotice = document.getElementById('processingNoticeBox');
    const appNotice = document.getElementById('approvedNoticeBox');
    const pass = document.getElementById('allocatedVehiclePass');

    if (booking.status === 'PROCESSING') {
        if (badge) {
            badge.className = 'badge-status-processing';
            badge.innerHTML = '<span class="pulse-amber"></span> STATUS: PROCESSING (Awaiting Dispatcher Approval)';
        }

        if (step1) step1.className = 'step-node completed';
        if (step2) step2.className = 'step-node active';
        if (step3) step3.className = 'step-node';
        if (conn1) conn1.className = 'step-connector completed';
        if (conn2) conn2.className = 'step-connector';

        if (procNotice) procNotice.style.display = 'flex';
        if (appNotice) appNotice.style.display = 'none';
        if (pass) pass.style.display = 'none';

    } else if (booking.status === 'APPROVED') {
        if (badge) {
            badge.className = 'badge-status-approved';
            badge.innerHTML = '✅ STATUS: APPROVED &bull; VEHICLE ALLOCATED';
        }

        if (step1) step1.className = 'step-node completed';
        if (step2) step2.className = 'step-node completed';
        if (step3) step3.className = 'step-node completed approved-state';
        if (conn1) conn1.className = 'step-connector completed';
        if (conn2) conn2.className = 'step-connector completed';

        if (procNotice) procNotice.style.display = 'none';
        if (appNotice) appNotice.style.display = 'flex';
        if (pass) pass.style.display = 'block';

        const passPlate = document.getElementById('passVehiclePlate');
        const passDriver = document.getElementById('passDriverName');
        const passPhone = document.getElementById('passDriverPhone');
        const passModel = document.getElementById('passVehicleModel');
        const passRoute = document.getElementById('passRoute');
        const passAmount = document.getElementById('passAmount');
        const passTime = document.getElementById('passApprovalTime');

        if (passPlate) passPlate.textContent = booking.allocatedVehicleNumber || 'KA-01-EF-4821';
        if (passDriver) passDriver.textContent = booking.allocatedDriver || 'Senior Pilot Assigned';
        if (passPhone) passPhone.textContent = booking.driverPhone || '+91 98450 12345';
        if (passModel) passModel.textContent = booking.vehicleName;
        if (passRoute) passRoute.textContent = `${booking.source} ➔ ${booking.destination} (${booking.totalBillableKms} KM)`;
        if (passAmount) passAmount.textContent = formatRupees(booking.grandTotal);
        if (passTime) passTime.textContent = booking.approvalTime || new Date().toLocaleString();

        showToast(`🎉 Requisition ${booking.id} APPROVED! Vehicle ${booking.allocatedVehicleNumber} Allocated!`, 'success');
        stopLivePolling();
    }
}

function hideTrackerCard() {
    const tracker = document.getElementById('userBookingTracker');
    if (tracker) tracker.style.display = 'none';
    stopLivePolling();
}

function startLivePolling(bookingId) {
    stopLivePolling();
    pollIntervalTimer = setInterval(async () => {
        try {
            const resp = await fetch(`/api/bookings?id=${bookingId}`);
            if (resp.ok) {
                const b = await resp.json();
                updateTrackerStatus(b);
                if (b.status !== 'PROCESSING') {
                    stopLivePolling();
                }
            }
        } catch (e) {
            console.error('Polling error', e);
        }
    }, 2500);
}

function stopLivePolling() {
    if (pollIntervalTimer) {
        clearInterval(pollIntervalTimer);
        pollIntervalTimer = null;
    }
}

// -----------------------------------------------------------------------------
// 5. Dispatcher / Approval Desk Portal Functions
// -----------------------------------------------------------------------------
async function fetchBookings() {
    try {
        const resp = await fetch('/api/bookings');
        if (!resp.ok) return;
        const list = await resp.json();

        renderBookingsTable(list);

        const totalEl = document.getElementById('totalReqCount');
        const pendingEl = document.getElementById('pendingReqCount');
        const appEl = document.getElementById('approvedReqCount');
        const badgePending = document.getElementById('pendingCountBadge');

        const pendingCount = list.filter(b => b.status === 'PROCESSING').length;
        const approvedCount = list.filter(b => b.status === 'APPROVED').length;

        if (totalEl) totalEl.textContent = list.length;
        if (pendingEl) pendingEl.textContent = pendingCount;
        if (appEl) appEl.textContent = approvedCount;
        if (badgePending) badgePending.textContent = pendingCount;

        if (activeUserBookingId) {
            const activeBooking = list.find(b => b.id === activeUserBookingId);
            if (activeBooking) {
                updateTrackerStatus(activeBooking);
            }
        }
    } catch (e) {
        console.error('Failed to load bookings list:', e);
    }
}

function renderBookingsTable(bookingsList) {
    const tbody = document.getElementById('bookingsTableBody');
    if (!tbody) return;

    if (!bookingsList || bookingsList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center; padding: 2rem;">No requisitions in the system yet.</td></tr>`;
        return;
    }

    tbody.innerHTML = bookingsList.map(b => {
        let statusBadge = '';
        let actionButtons = '';
        let allocationInfo = '-';

        if (b.status === 'PROCESSING') {
            statusBadge = `<span class="badge-table-processing"><span class="pulse-amber"></span> PROCESSING</span>`;
            actionButtons = `
                <button type="button" class="btn-action-approve" onclick="approveRequisition('${b.id}')">
                    Approve & Allocate
                </button>
                <button type="button" class="btn-action-reject" onclick="rejectRequisition('${b.id}')">
                    Reject
                </button>
            `;
            allocationInfo = `<em style="color:#f59e0b;">Awaiting allocation</em>`;
        } else if (b.status === 'APPROVED') {
            statusBadge = `<span class="badge-table-approved">✅ APPROVED</span>`;
            actionButtons = `<span style="color:#10b981; font-weight:700;">Dispatched</span>`;
            allocationInfo = `
                <strong>${escapeHtml(b.allocatedVehicleNumber || 'Assigned')}</strong><br>
                <small style="color:#94a3b8;">Driver: ${escapeHtml(b.allocatedDriver || 'Assigned')}</small>
            `;
        } else {
            statusBadge = `<span class="badge-table-rejected">❌ REJECTED</span>`;
            actionButtons = `<span style="color:#f87171;">Closed</span>`;
            allocationInfo = `<span style="color:#94a3b8;">Not allocated</span>`;
        }

        const tripBadge = b.tripType === 'round-trip' ? '<span style="color:#c084fc;">🔁 Round</span>' : '<span style="color:#38bdf8;">One-Way</span>';

        return `
            <tr>
                <td><strong>${escapeHtml(b.id)}</strong><br><small style="color:#64748b;">${escapeHtml(b.createdAt || '')}</small></td>
                <td><strong>${escapeHtml(b.companyName)}</strong><br><small style="color:#94a3b8;">${escapeHtml(b.contactName)} (${escapeHtml(b.contactPhone)})</small></td>
                <td><strong>${escapeHtml(b.vehicleName)}</strong><br><small style="color:#94a3b8;">Cargo: ${escapeHtml(b.cargoDescription || 'Commercial')}</small></td>
                <td>${escapeHtml(b.source)} ➔ ${escapeHtml(b.destination)}<br><small>${b.totalBillableKms} KM &bull; ${tripBadge}</small></td>
                <td><strong style="color:#38bdf8; font-size:1rem;">${formatRupees(b.grandTotal)}</strong></td>
                <td>${statusBadge}</td>
                <td>${allocationInfo}</td>
                <td>${actionButtons}</td>
            </tr>
        `;
    }).join('');
}

async function approveRequisition(bookingId) {
    try {
        const resp = await fetch('/api/admin/approve', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ bookingId: bookingId })
        });

        if (!resp.ok) throw new Error('Approval API call failed');
        const updated = await resp.json();

        showToast(`Requisition ${bookingId} approved! Vehicle allocated: ${updated.allocatedVehicleNumber}`, 'success');
        await fetchBookings();

        if (activeUserBookingId === bookingId) {
            updateTrackerStatus(updated);
        }
    } catch (e) {
        console.error('Approval failed:', e);
        showToast('Approval action failed.', 'warning');
    }
}

async function rejectRequisition(bookingId) {
    if (!confirm(`Are you sure you want to reject requisition #${bookingId}?`)) return;

    try {
        const resp = await fetch('/api/admin/reject', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ bookingId: bookingId, reason: 'Capacity constraints' })
        });

        if (!resp.ok) throw new Error('Rejection failed');
        showToast(`Requisition ${bookingId} marked as REJECTED.`, 'warning');
        await fetchBookings();
    } catch (e) {
        console.error('Reject failed:', e);
    }
}

// -----------------------------------------------------------------------------
// Utilities
// -----------------------------------------------------------------------------
function formatRupees(amount) {
    if (typeof amount !== 'number') amount = parseFloat(amount) || 0;
    return '₹' + amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatNumber(num) {
    if (typeof num !== 'number') num = parseFloat(num) || 0;
    return num.toLocaleString('en-IN');
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${type === 'success' ? '✅' : '⚠️'}</span> <span>${message}</span>`;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}