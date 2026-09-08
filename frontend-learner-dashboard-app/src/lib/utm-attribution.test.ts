// @vitest-environment jsdom
//
// The repo default is `environment: "node"`, which has no window,
// sessionStorage or fetch — and this module is entirely about all three.
import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import {
  captureUtmOnce,
  getStoredUtm,
  hasStoredUtm,
  trackUtmAttribution,
} from "./utm-attribution";

// `hostname` is included because config/baseUrl.ts reads it at module-eval
// time when constants/urls is imported; a location stub without it breaks the
// import graph rather than the assertion.
const setUrl = (search: string) => {
  Object.defineProperty(window, "location", {
    value: {
      search,
      pathname: "/audience-response",
      hostname: "learner.example.com",
      href: `https://learner.example.com/audience-response${search}`,
    } as Location,
    writable: true,
    configurable: true,
  });
};

describe("first-touch capture", () => {
  beforeEach(() => {
    sessionStorage.clear();
    setUrl("");
  });

  it("banks the campaign off the landing URL", () => {
    setUrl("?instituteId=1&utm_source=meta&utm_medium=cpc&utm_campaign=diwali");
    captureUtmOnce();
    expect(getStoredUtm()).toEqual({
      utm_source: "meta",
      utm_medium: "cpc",
      utm_campaign: "diwali",
    });
  });

  // FIRST touch wins. A learner who arrives from an ad, browses the catalogue
  // and then reaches the form through an untagged internal link must still be
  // credited to the ad — re-capturing would overwrite the campaign with nothing.
  it("does not overwrite the first campaign on a later untagged page", () => {
    setUrl("?utm_source=meta");
    captureUtmOnce();
    setUrl("?instituteId=1");
    captureUtmOnce();
    expect(getStoredUtm()).toEqual({ utm_source: "meta" });
  });

  it("stores nothing at all for an untagged arrival", () => {
    setUrl("?instituteId=1&audienceId=9");
    captureUtmOnce();
    expect(hasStoredUtm()).toBe(false);
    expect(getStoredUtm()).toEqual({});
  });

  it("caps an absurdly long value rather than storing it whole", () => {
    setUrl(`?utm_campaign=${"a".repeat(500)}`);
    captureUtmOnce();
    expect(getStoredUtm().utm_campaign).toHaveLength(120);
  });

  it("answers empty when storage holds junk", () => {
    sessionStorage.setItem("vac_utm_first_touch", "{not json");
    expect(getStoredUtm()).toEqual({});
  });
});

describe("reporting a touch", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    sessionStorage.clear();
    setUrl("");
    fetchMock = vi.fn(() => Promise.resolve({ ok: true } as Response));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => vi.unstubAllGlobals());

  const capture = () => {
    setUrl("?utm_source=meta&utm_campaign=diwali");
    captureUtmOnce();
  };

  it("posts the banked campaign with the identity", async () => {
    capture();
    trackUtmAttribution({
      instituteId: "inst-1",
      userId: "user-1",
      sourceType: "AUDIENCE",
      sourceId: "aud-9",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    expect(body).toMatchObject({
      institute_id: "inst-1",
      user_id: "user-1",
      source_type: "AUDIENCE",
      source_id: "aud-9",
      utm_source: "meta",
      utm_campaign: "diwali",
    });
    // keepalive: the successful submit is normally followed straight away by a
    // thank-you redirect or a payment gateway hop.
    expect(init.keepalive).toBe(true);
  });

  // An untagged arrival is the absence of attribution, not a data point.
  it("sends nothing when this session carries no campaign", () => {
    trackUtmAttribution({
      instituteId: "inst-1",
      userId: "user-1",
      sourceType: "AUDIENCE",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sends nothing when there is nobody to attach the touch to", () => {
    capture();
    trackUtmAttribution({ instituteId: "inst-1", sourceType: "AUDIENCE" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sends nothing without an institute", () => {
    capture();
    trackUtmAttribution({ userId: "user-1", sourceType: "AUDIENCE" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("accepts an email-only identity, for surfaces that have no user id yet", () => {
    capture();
    trackUtmAttribution({
      instituteId: "inst-1",
      email: "learner@example.com",
      sourceType: "CATALOGUE",
      sourceId: "site:contact-form",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  // By the time this runs the learner has already done the thing they came to
  // do; telemetry must never surface as an error on a form that succeeded.
  it("never throws when the network call blows up", () => {
    capture();
    vi.stubGlobal("fetch", () => {
      throw new Error("offline");
    });
    expect(() =>
      trackUtmAttribution({
        instituteId: "inst-1",
        userId: "user-1",
        sourceType: "ASSESSMENT",
      })
    ).not.toThrow();
  });
});
