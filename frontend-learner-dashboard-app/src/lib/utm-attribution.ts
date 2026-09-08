import { BASE_URL } from "@/constants/urls";

/**
 * First-touch UTM capture, and reporting it against the person it produced.
 *
 * WHY BOTH HALVES LIVE HERE: the UTM parameters are on the URL the learner
 * LANDS on, and the identity is only known on the page they SUBMIT from —
 * often several navigations later, sometimes a different route entirely
 * (catalogue → course → enrol). Capturing on landing and reading at submit is
 * the only ordering that works, so the store and the beacon have to agree on
 * one key. They did not use to: the catalogue tracker had its own copy, and
 * every other surface had none, which is why an admin could generate a tagged
 * link for an audience form and then find nothing recorded against the lead.
 *
 * The store is sessionStorage, not localStorage, deliberately. Attribution
 * belongs to THIS visit; a campaign click three weeks ago should not be
 * credited with today's organic signup.
 */

export const UTM_KEYS = [
  "utm_source",
  "utm_medium",
  "utm_campaign",
  "utm_content",
  "utm_term",
] as const;

export type UtmKey = (typeof UTM_KEYS)[number];
export type UtmValues = Partial<Record<UtmKey, string>>;

/** Must match every reader — including the catalogue tracker, which delegates here. */
const UTM_STORE = "vac_utm_first_touch";
const LANDING_STORE = "vac_utm_landing";

/** Mirrors the backend's allow-list; anything else is dropped server-side. */
export type UtmSourceType =
  | "AUDIENCE"
  | "LIVE_SESSION"
  | "ASSESSMENT"
  | "ENROLL_INVITE"
  | "PRODUCT_PAGE"
  | "CATALOGUE";

/**
 * Record the UTM parameters on the current URL, once per browsing session.
 *
 * FIRST touch wins: a learner who arrives from an Instagram ad, wanders the
 * catalogue and then reaches a form through an untagged internal link should
 * still be credited to Instagram. Re-capturing on every page would instead
 * overwrite the campaign with nothing, which is the classic way self-built
 * attribution reports come out empty.
 */
export const captureUtmOnce = (): void => {
  try {
    if (sessionStorage.getItem(UTM_STORE)) return;
    const params = new URLSearchParams(window.location.search);
    const utm: Record<string, string> = {};
    for (const key of UTM_KEYS) {
      const value = params.get(key);
      if (value) utm[key] = value.slice(0, 120);
    }
    if (Object.keys(utm).length === 0) return;
    sessionStorage.setItem(UTM_STORE, JSON.stringify(utm));
    // Path only — the query string is where any PII would be.
    sessionStorage.setItem(LANDING_STORE, window.location.pathname.slice(0, 512));
  } catch {
    /* storage unavailable (private mode) — attribution is best-effort */
  }
};

export const getStoredUtm = (): UtmValues => {
  try {
    return JSON.parse(sessionStorage.getItem(UTM_STORE) || "{}") as UtmValues;
  } catch {
    return {};
  }
};

export const hasStoredUtm = (): boolean => Object.keys(getStoredUtm()).length > 0;

const getLandingPath = (): string | undefined => {
  try {
    return sessionStorage.getItem(LANDING_STORE) || undefined;
  } catch {
    return undefined;
  }
};

export interface TrackUtmInput {
  instituteId?: string | null;
  /** Known for most surfaces; the server matches on contact details otherwise. */
  userId?: string | null;
  email?: string | null;
  mobileNumber?: string | null;
  sourceType: UtmSourceType;
  /** The campaign / session / assessment / invite / page the link pointed at. */
  sourceId?: string | null;
}

/**
 * Report one campaign touch after a SUCCESSFUL submission.
 *
 * Fire-and-forget by design, and it never throws: by the time this runs the
 * learner has already done the thing they came to do, so a telemetry failure
 * must not surface as an error on a form that in fact succeeded.
 *
 * Sends nothing when this session carries no UTM parameters — an untagged
 * arrival is the absence of attribution, and storing it would bury the real
 * campaigns under blank rows.
 */
export const trackUtmAttribution = (input: TrackUtmInput): void => {
  try {
    const utm = getStoredUtm();
    if (!input?.instituteId || Object.keys(utm).length === 0) return;
    if (!input.userId && !input.email && !input.mobileNumber) return;

    const body = JSON.stringify({
      institute_id: input.instituteId,
      user_id: input.userId ?? undefined,
      email: input.email ?? undefined,
      mobile_number: input.mobileNumber ?? undefined,
      source_type: input.sourceType,
      source_id: input.sourceId ?? undefined,
      utm_source: utm.utm_source,
      utm_medium: utm.utm_medium,
      utm_campaign: utm.utm_campaign,
      utm_content: utm.utm_content,
      utm_term: utm.utm_term,
      referrer: document.referrer || undefined,
      landing_url: getLandingPath(),
    });

    const url = `${BASE_URL}/admin-core-service/open/v1/utm/track`;
    // keepalive so the report survives the navigation that usually follows a
    // successful submit (a thank-you redirect, or a payment gateway hop).
    void fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      keepalive: true,
    }).catch(() => {
      /* attribution is best-effort */
    });
  } catch {
    /* never let telemetry break a submission */
  }
};
