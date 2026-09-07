import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { urlCourseDetails } from "@/constants/urls";
import { getPublicUrlWithoutLogin } from "@/services/upload_file";
import { OfferBadge, PriceWithMrp } from "@/components/common/price-with-mrp";
import { cn } from "@/lib/utils";

/**
 * A CURATED strip of courses — "new", "on sale", one tag, or a hand-picked
 * list — for promoting a few courses on a landing page.
 *
 * This is deliberately not `productCourseGrid`: that component renders the
 * WHOLE catalogue with search, sort and a filter sidebar, and has no way to
 * limit or curate what it shows. Pricing and the discount ribbon reuse the
 * shared PriceWithMrp/OfferBadge so a promoted card matches a catalogue card
 * exactly, including reader-mode hiding and the FREE ribbon.
 */

/** Where the strip's courses come from. */
export type CourseShowcaseSource = "newest" | "onSale" | "tag" | "picked";

interface ShowcaseCourse {
    id: string;
    title: string;
    description: string;
    thumbnailId: string;
    price: number;
    elevatedPrice?: number;
    currency?: string;
    tags: string[];
    level?: string;
    enrollInviteId?: string;
    packageSessionId?: string;
}

export interface CourseShowcaseProps {
    title?: string;
    subtitle?: string;
    source?: CourseShowcaseSource;
    /** Tag to match when source is "tag" (case-insensitive). */
    tag?: string;
    /** Course ids to show, in order, when source is "picked". */
    courseIds?: string[];
    limit?: number;
    layout?: "row" | "grid";
    backgroundColor?: string;
    instituteId?: string;
    tagName?: string;
}

const splitTags = (raw: unknown): string[] =>
    typeof raw === "string"
        ? raw.split(",").map((s) => s.trim()).filter(Boolean)
        : Array.isArray(raw)
          ? (raw as unknown[]).map((s) => String(s).trim()).filter(Boolean)
          : [];

/** Resolves a media-service file id to a URL; a direct URL passes through. */
const ShowcaseImage: React.FC<{ fileId: string; alt: string }> = ({ fileId, alt }) => {
    const [url, setUrl] = useState("");
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        let alive = true;
        if (!fileId) {
            setFailed(true);
            return;
        }
        getPublicUrlWithoutLogin(fileId)
            .then((u) => alive && (u ? setUrl(u) : setFailed(true)))
            .catch(() => alive && setFailed(true));
        return () => {
            alive = false;
        };
    }, [fileId]);

    if (failed) {
        return (
            <div className="flex h-full w-full items-center justify-center bg-catalogue-bg-muted px-4 text-center text-sm font-medium text-catalogue-text-muted">
                {alt}
            </div>
        );
    }
    if (!url) return <div className="h-full w-full animate-pulse bg-catalogue-bg-muted" />;
    return <img src={url} alt={alt} loading="lazy" className="h-full w-full object-cover" />;
};

export const CourseShowcaseComponent: React.FC<CourseShowcaseProps> = ({
    title,
    subtitle,
    source = "newest",
    tag,
    courseIds,
    limit = 3,
    layout = "row",
    backgroundColor,
    instituteId,
    tagName,
}) => {
    const { t } = useTranslation("coursePlayerB");
    const navigate = useNavigate();
    const [courses, setCourses] = useState<ShowcaseCourse[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let alive = true;
        if (!instituteId) {
            setLoading(false);
            return;
        }
        // Same open search endpoint the catalogue grid uses. `createdAt,desc`
        // is what makes "newest" simply the first N — no client-side date sort.
        axios
            .post(
                urlCourseDetails,
                {
                    status: [],
                    level_ids: [],
                    faculty_ids: [],
                    search_by_name: "",
                    tag: [],
                    min_percentage_completed: 0,
                    max_percentage_completed: 0,
                },
                {
                    params: { instituteId, page: 0, size: 200, sort: "createdAt,desc" },
                    headers: { "Content-Type": "application/json" },
                },
            )
            .then((res) => {
                if (!alive) return;
                const raw = res.data?.content || res.data || [];
                setCourses(
                    (raw as Array<Record<string, any>>).map((c) => ({
                        id: c.id || c.packageId,
                        title: c.package_name || "",
                        description: String(c.course_html_description_html || "")
                            .replace(/<[^>]*>/g, "")
                            .replace(/&nbsp;/g, " ")
                            .trim(),
                        thumbnailId:
                            c.course_preview_image_media_id ||
                            c.course_banner_media_id ||
                            c.thumbnail_file_id ||
                            "",
                        price: c.min_plan_actual_price || 0,
                        elevatedPrice:
                            typeof c.min_plan_elevated_price === "number"
                                ? c.min_plan_elevated_price
                                : undefined,
                        currency: c.currency,
                        tags: splitTags(c.comma_separeted_tags),
                        level: c.level_name,
                        enrollInviteId: c.enroll_invite_id,
                        packageSessionId: c.package_session_id,
                    })),
                );
            })
            .catch((e) => console.error("[CourseShowcase] fetch failed:", e))
            .finally(() => alive && setLoading(false));
        return () => {
            alive = false;
        };
    }, [instituteId]);

    const shown = useMemo(() => {
        let list = courses;
        if (source === "onSale") {
            list = list.filter(
                (c) => typeof c.elevatedPrice === "number" && c.elevatedPrice > c.price,
            );
        } else if (source === "tag") {
            const want = (tag || "").trim().toLowerCase();
            list = want
                ? list.filter((c) => c.tags.some((x) => x.toLowerCase() === want))
                : [];
        } else if (source === "picked") {
            const order = (courseIds || []).filter(Boolean);
            const byId = new Map(list.map((c) => [c.id, c]));
            list = order.map((id) => byId.get(id)).filter(Boolean) as ShowcaseCourse[];
        }
        return list.slice(0, Math.max(1, limit));
    }, [courses, source, tag, courseIds, limit]);

    const openCourse = (c: ShowcaseCourse) =>
        navigate({
            to: `/${tagName}/${c.id}`,
            search: {
                enrollInviteId: c.enrollInviteId,
                packageSessionId: c.packageSessionId,
                level: c.level,
            },
        });

    // An empty curated strip is a configuration mistake (a tag that matches
    // nothing, say) — render nothing rather than an empty titled band.
    if (!loading && shown.length === 0) return null;

    return (
        <section
            className="catalogue-section bg-catalogue-bg"
            style={backgroundColor ? { backgroundColor } : undefined} // design-lint-ignore: author-picked page-builder color
        >
            <div className="catalogue-shell">
                {title && (
                    <h2 className="catalogue-h2 mb-2 text-center text-catalogue-text-primary">{title}</h2>
                )}
                {subtitle && (
                    <p className="catalogue-lead catalogue-measure mx-auto mb-10 text-center text-catalogue-text-muted">
                        {subtitle}
                    </p>
                )}

                <div
                    className={cn(
                        "grid gap-6",
                        layout === "row"
                            ? "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3"
                            : "grid-cols-1 sm:grid-cols-2 lg:grid-cols-4",
                    )}
                >
                    {loading
                        ? Array.from({ length: Math.min(limit, 4) }).map((_, i) => (
                              <div
                                  key={i}
                                  className="h-80 animate-pulse rounded-catalogue-lg bg-catalogue-bg-muted"
                              />
                          ))
                        : shown.map((c) => (
                              <button
                                  key={c.id}
                                  type="button"
                                  onClick={() => openCourse(c)}
                                  className="group flex flex-col overflow-hidden rounded-catalogue-lg border border-catalogue-border-subtle bg-catalogue-bg-elevated text-start shadow-sm transition-transform duration-300 ease-out hover:-translate-y-1"
                              >
                                  <div className="relative aspect-video w-full overflow-hidden bg-catalogue-bg-muted">
                                      <ShowcaseImage fileId={c.thumbnailId} alt={c.title} />
                                      <div className="absolute start-3 top-3">
                                          <OfferBadge actual={c.price} elevated={c.elevatedPrice} />
                                      </div>
                                  </div>
                                  <div className="flex flex-1 flex-col gap-2 p-5">
                                      <h3 className="text-base font-semibold text-catalogue-text-primary">
                                          {c.title}
                                      </h3>
                                      {c.description && (
                                          <p className="line-clamp-2 text-sm text-catalogue-text-muted">
                                              {c.description}
                                          </p>
                                      )}
                                      <div className="mt-auto pt-3">
                                          <PriceWithMrp
                                              actual={c.price}
                                              elevated={c.elevatedPrice}
                                              currency={c.currency}
                                              layout="inline"
                                          />
                                          <span className="catalogue-btn catalogue-btn-primary mt-3 w-full justify-center">
                                              {t("courseCatalog.viewCourse", { course: "Course" })}
                                          </span>
                                      </div>
                                  </div>
                              </button>
                          ))}
                </div>
            </div>
        </section>
    );
};

export default CourseShowcaseComponent;
