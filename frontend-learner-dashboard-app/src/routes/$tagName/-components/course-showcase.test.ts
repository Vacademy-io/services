// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";

// The component navigates on card click, and TanStack's useNavigate throws
// without a router context. The tests below assert rendering, not routing.
vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => () => {},
  useParams: () => ({ tagName: "tag" }),
  useRouter: () => ({ navigate: () => {} }),
  Link: () => null,
}));
import React from "react";
import { renderToString } from "react-dom/server";
import { JsonRenderer } from "./JsonRenderer";

/**
 * `courseShowcase` is the curated alternative to `productCourseGrid`, which
 * always renders the whole catalogue and cannot be limited. These assert the
 * component is wired into the renderer and paints its own chrome — the course
 * data itself arrives from an effect, so SSR shows the loading skeletons.
 */
const page = (props: Record<string, unknown>) => ({
  id: "p", route: "p", title: "p",
  components: [{ id: "s", type: "courseShowcase", enabled: true, props }],
});

const render = (props: Record<string, unknown>) =>
  renderToString(
    React.createElement(JsonRenderer, {
      page: page(props), globalSettings: {} as never,
      instituteId: "inst", tagName: "tag",
    } as never),
  );

describe("courseShowcase", () => {
  it("is a known component type (not the unknown-type fallback)", () => {
    const html = render({ title: "Hot right now", limit: 3 });
    expect(html).toContain("Hot right now");
  });

  it("renders its subtitle when given one", () => {
    expect(render({ title: "New", subtitle: "Fresh from the studio" }))
      .toContain("Fresh from the studio");
  });

  it("shows a skeleton per course while loading, capped at 4", () => {
    const html = render({ title: "X", limit: 8 });
    expect((html.match(/animate-pulse/g) || []).length).toBe(4);
  });

  it("honours the limit for small counts", () => {
    const html = render({ title: "X", limit: 2 });
    expect((html.match(/animate-pulse/g) || []).length).toBe(2);
  });
});
