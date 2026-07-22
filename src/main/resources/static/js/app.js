const state = {
  kpi: null,
};

const reportDateInput = document.querySelector("#reportDate");
const loadButton = document.querySelector("#loadButton");
const generateButton = document.querySelector("#generateButton");
const copyButton = document.querySelector("#copyButton");
const resetButton = document.querySelector("#resetButton");
const slackPreview = document.querySelector("#slackPreview");
const alertArea = document.querySelector("#alertArea");
const dataMode = document.querySelector("#dataMode");
const currentMdmLockedTotal = document.querySelector("#currentMdmLockedTotal");
const gradingBacklog = document.querySelector("#gradingBacklog");
const unpackedReturnPackages = document.querySelector("#unpackedReturnPackages");
const unpackedReturnPackagesDate = document.querySelector("#unpackedReturnPackagesDate");
const notes = document.querySelector("#notes");
const totalLocked = document.querySelector("#totalLocked");
const lockedCalculation = document.querySelector("#lockedCalculation");

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function selectedReportDateIso() {
  const value = reportDateInput.value;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new Error("Select a valid reporting date before loading KPI data.");
  }
  return value;
}

function showAlert(message, type = "danger") {
  alertArea.innerHTML = `<div class="alert alert-${type}" role="alert">${message}</div>`;
}

function clearAlert() {
  alertArea.innerHTML = "";
}

function resizeSlackPreview(element) {
  element.style.height = "auto";
  element.style.height = `${element.scrollHeight}px`;
}

function setLoading(button, loading) {
  button.disabled = loading;
  button.dataset.originalText = button.dataset.originalText || button.textContent;
  button.textContent = loading ? "Loading..." : button.dataset.originalText;
}

async function parseResponse(response) {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.message || "Unable to read KPI data.");
  }
  return body;
}

function renderKpiCards(kpi) {
  document.querySelectorAll("[data-kpi]").forEach((element) => {
    element.textContent = kpi ? kpi[element.dataset.kpi] : "-";
  });
  renderCalculation();
}

function renderCalculation() {
  if (!state.kpi) {
    totalLocked.textContent = "-";
    lockedCalculation.textContent = "Load KPI data to see the calculation.";
    return;
  }
  const userLocked = state.kpi.userLocked ?? 0;
  const total = state.kpi.mdmLocked + state.kpi.fmiLocked + state.kpi.frpLocked + userLocked;
  totalLocked.textContent = total;
  lockedCalculation.textContent = `${state.kpi.mdmLocked} + ${state.kpi.fmiLocked} + ${state.kpi.frpLocked} + ${userLocked} = ${total}`;
}

function manualInteger(input) {
  if (input.value === "") {
    return 0;
  }
  const value = Number(input.value);
  return Number.isInteger(value) ? value : NaN;
}

function validateManualInputs() {
  const values = [
    manualInteger(currentMdmLockedTotal),
    manualInteger(gradingBacklog),
    manualInteger(unpackedReturnPackages),
  ];
  if (!values.every((value) => Number.isInteger(value) && value >= 0)) {
    showAlert("Manual numeric values must be whole numbers and cannot be negative.");
    return false;
  }
  if (manualInteger(unpackedReturnPackages) > 0 && !unpackedReturnPackagesDate.value) {
    showAlert("Enter the Retourenpakete date when unpacked return packages is greater than 0.");
    return false;
  }
  return true;
}

async function loadMode() {
  const response = await fetch("/api/mode");
  const body = await parseResponse(response);
  dataMode.textContent = body.mode;
}

async function loadKpiData() {
  clearAlert();
  slackPreview.value = "";
  resizeSlackPreview(slackPreview);
  setLoading(loadButton, true);
  try {
    const response = await fetch(`/api/kpi?date=${encodeURIComponent(selectedReportDateIso())}`);
    state.kpi = await parseResponse(response);
    renderKpiCards(state.kpi);
    showAlert("KPI data loaded. Enter the two Odoo values, then generate the preview.", "success");
  } catch (error) {
    state.kpi = null;
    renderKpiCards(null);
    showAlert(error.message);
  } finally {
    setLoading(loadButton, false);
  }
}

async function generatePreview() {
  clearAlert();
  if (!state.kpi) {
    showAlert("Load KPI data before generating the preview.");
    return;
  }
  if (!validateManualInputs()) {
    return;
  }

  const payload = {
    reportDate: selectedReportDateIso(),
    receivedInOdoo: state.kpi.receivedInOdoo,
    mdmLocked: state.kpi.mdmLocked,
    fmiLocked: state.kpi.fmiLocked,
    frpLocked: state.kpi.frpLocked,
    userLocked: state.kpi.userLocked ?? 0,
    successful: state.kpi.successful,
    failed: state.kpi.failed,
    gradingTotal: state.kpi.gradingTotal,
    currentMdmLockedTotal: manualInteger(currentMdmLockedTotal),
    gradingBacklog: manualInteger(gradingBacklog),
    unpackedReturnPackages: manualInteger(unpackedReturnPackages),
    unpackedReturnPackagesDate: unpackedReturnPackagesDate.value || null,
    notes: notes.value,
  };

  const response = await fetch("/api/preview", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await parseResponse(response);
  slackPreview.value = body.message;
  resizeSlackPreview(slackPreview);
  showAlert("Slack preview generated. Review it before copying.", "success");
}

async function copyMessage() {
  clearAlert();
  if (!slackPreview.value) {
    showAlert("Generate a preview before copying.");
    return;
  }
  await navigator.clipboard.writeText(slackPreview.value);
  showAlert("Slack message copied.", "success");
}

function resetForm() {
  clearAlert();
  state.kpi = null;
  reportDateInput.value = todayIso();
  currentMdmLockedTotal.value = "0";
  gradingBacklog.value = "0";
  unpackedReturnPackages.value = "0";
  unpackedReturnPackagesDate.value = "";
  notes.value = "";
  slackPreview.value = "";
  resizeSlackPreview(slackPreview);
  renderKpiCards(null);
}

reportDateInput.value = todayIso();
currentMdmLockedTotal.value = currentMdmLockedTotal.value || "0";
gradingBacklog.value = gradingBacklog.value || "0";
unpackedReturnPackages.value = unpackedReturnPackages.value || "0";
resizeSlackPreview(slackPreview);
loadButton.addEventListener("click", loadKpiData);
generateButton.addEventListener("click", generatePreview);
copyButton.addEventListener("click", copyMessage);
resetButton.addEventListener("click", resetForm);
[currentMdmLockedTotal, gradingBacklog, unpackedReturnPackages, unpackedReturnPackagesDate, notes, reportDateInput].forEach((element) => {
  element.addEventListener("input", () => resizeSlackPreview(slackPreview));
});
currentMdmLockedTotal.addEventListener("input", renderCalculation);
gradingBacklog.addEventListener("input", renderCalculation);
loadMode().catch(() => {
  dataMode.textContent = "Unknown";
});
