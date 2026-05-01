export function formatCurrency(value) {
  const amount = Number(value || 0);
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2
  }).format(amount);
}

export function formatDate(value) {
  if (!value) {
    return "Not available";
  }

  try {
    const parsedValue = new Date(value);

    return new Intl.DateTimeFormat("en-IN", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "Asia/Kolkata"
    }).format(parsedValue);
  } catch (error) {
    return value;
  }
}

export function formatUtcDate(value) {
  if (!value) {
    return "Not available";
  }

  const textValue = String(value);
  const hasTimezone = /(?:z|[+-]\d{2}:?\d{2})$/i.test(textValue);

  return formatDate(hasTimezone ? textValue : `${textValue}Z`);
}

export function titleize(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}
