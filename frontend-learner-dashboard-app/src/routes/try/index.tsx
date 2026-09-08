import { useEffect, useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { CircleNotch, GraduationCap, Microphone, TextT } from "@phosphor-icons/react";
import { getTutorDemoTopics, startTutorDemo, type TutorDemoTopic } from "@/services/tutor-api";
import { writeTutorGuest } from "@/lib/tutorGuest";

interface TrySearch {
  done?: string;
  topic?: string;
}

/**
 * Public 3-minute lesson for tutezy.ai visitors: a name, a topic, then the
 * real tutor page with a guest token. No sign-up, one per visitor per day.
 */
export const Route = createFileRoute("/try/")({
  component: TryPage,
  validateSearch: (search: Record<string, unknown>): TrySearch => ({
    done: search.done ? String(search.done) : undefined,
    topic: search.topic ? String(search.topic) : undefined,
  }),
});

const TUTEZY = "https://tutezy.ai";

function TryPage() {
  const navigate = useNavigate();
  const search = Route.useSearch();
  const [topics, setTopics] = useState<TutorDemoTopic[] | null>(null);
  const [enabled, setEnabled] = useState(true);
  const [minutes, setMinutes] = useState(3);
  const [name, setName] = useState("");
  const [topic, setTopic] = useState(search.topic || "");
  const [mode, setMode] = useState<"VOICE" | "TEXT">("VOICE");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    getTutorDemoTopics()
      .then((r) => {
        setTopics(r.topics);
        setEnabled(r.enabled);
        setMinutes(r.minutes);
        if (!topic && r.topics[0]) setTopic(r.topics[0].key);
      })
      .catch(() => {
        setTopics([]);
        setEnabled(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const start = async () => {
    if (!name.trim() || !topic) {
      setError("Your first name and a topic, that's all we need.");
      return;
    }
    setError("");
    setBusy(true);
    try {
      const r = await startTutorDemo({ name: name.trim(), topicKey: topic, mode });
      writeTutorGuest({ token: r.token, boot: r.boot, minutes: r.minutes, name: name.trim(), topicKey: topic });
      navigate({
        to: "/study-library/courses/course-details/tutor",
        search: { courseId: "demo", packageSessionId: "demo", slideId: r.boot.slide_id, mode: mode === "VOICE" ? "voice" : "text", demo: "1" } as never,
      });
    } catch (e: unknown) {
      const detail = (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setError(detail || "Could not start the lesson. Please try again in a moment.");
      setBusy(false);
    }
  };

  return (
    <main className="fixed inset-0 overflow-y-auto bg-neutral-50 px-4 py-10 text-neutral-900">
      <div className="mx-auto max-w-lg">
        <div className="mb-6 flex items-center gap-2">
          <span className="grid size-9 place-items-center rounded-xl bg-neutral-900 text-white"><GraduationCap className="size-5" weight="fill" /></span>
          <div>
            <p className="text-sm font-semibold">Tutezy <span className="font-normal text-neutral-500">by Vacademy</span></p>
            <p className="text-xs text-neutral-500">A {minutes}-minute lesson with a live AI teacher</p>
          </div>
        </div>

        {search.done ? (
          <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm">
            <h1 className="text-2xl font-bold">That was your free lesson.</h1>
            <p className="mt-2 text-neutral-600">
              Imagine every student of yours getting this, on your own content, in your teachers&apos; voices. We set it up on a 20-minute call.
            </p>
            <a href={`${TUTEZY}/#demo`} className="mt-5 inline-block rounded-full bg-primary-500 px-5 py-2.5 text-sm font-semibold text-white">Book a demo</a>
            <a href={TUTEZY} className="ms-3 text-sm font-semibold text-neutral-600 hover:underline">Back to tutezy.ai</a>
          </section>
        ) : !enabled && topics !== null ? (
          <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm">
            <h1 className="text-2xl font-bold">The free lesson is taking a break.</h1>
            <p className="mt-2 text-neutral-600">Book a demo and we will run a full lesson on your own chapter.</p>
            <a href={`${TUTEZY}/#demo`} className="mt-5 inline-block rounded-full bg-primary-500 px-5 py-2.5 text-sm font-semibold text-white">Book a demo</a>
          </section>
        ) : (
          <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm">
            <h1 className="text-2xl font-bold">Take a {minutes}-minute lesson. No sign-up.</h1>
            <p className="mt-1 text-sm text-neutral-600">The teacher writes on a whiteboard, speaks, asks you a question and listens to your answer.</p>

            <label className="mt-5 block">
              <span className="text-xs font-semibold uppercase tracking-wide text-neutral-500">What should the teacher call you?</span>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={40}
                autoComplete="given-name"
                placeholder="Your first name"
                className="mt-1 w-full rounded-xl border border-neutral-300 px-3 py-2.5 outline-none focus:border-primary-500"
              />
            </label>

            <p className="mt-5 text-xs font-semibold uppercase tracking-wide text-neutral-500">Pick a topic</p>
            {topics === null ? (
              <p className="mt-2 flex items-center gap-2 text-sm text-neutral-500"><CircleNotch className="size-4 animate-spin" /> Loading topics…</p>
            ) : (
              <div className="mt-2 grid gap-2 sm:grid-cols-2">
                {topics.map((t) => (
                  <button
                    key={t.key}
                    type="button"
                    onClick={() => setTopic(t.key)}
                    aria-pressed={topic === t.key}
                    className={`flex items-center gap-2 rounded-xl border px-3 py-2.5 text-start text-sm ${topic === t.key ? "border-primary-500 bg-primary-50 font-semibold" : "border-neutral-200 hover:bg-neutral-50"}`}
                  >
                    {t.emoji && <span className="text-lg" aria-hidden="true">{t.emoji}</span>}
                    <span>{t.title}{t.language === "hi" ? <span className="ms-1 text-xs text-neutral-500">हिंदी</span> : null}</span>
                  </button>
                ))}
              </div>
            )}

            <p className="mt-5 text-xs font-semibold uppercase tracking-wide text-neutral-500">How do you want to talk?</p>
            <div className="mt-2 flex gap-2" role="group" aria-label="Lesson mode">
              <button type="button" onClick={() => setMode("VOICE")} aria-pressed={mode === "VOICE"} className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm ${mode === "VOICE" ? "border-primary-500 bg-primary-50 font-semibold" : "border-neutral-200"}`}>
                <Microphone className="size-4" /> Voice (needs a mic)
              </button>
              <button type="button" onClick={() => setMode("TEXT")} aria-pressed={mode === "TEXT"} className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm ${mode === "TEXT" ? "border-primary-500 bg-primary-50 font-semibold" : "border-neutral-200"}`}>
                <TextT className="size-4" /> Text only
              </button>
            </div>

            {error && <p role="alert" className="mt-4 rounded-lg border border-danger-200 bg-danger-50 px-3 py-2 text-sm text-danger-700">{error}</p>}

            <button
              type="button"
              onClick={() => void start()}
              disabled={busy || topics === null || topics.length === 0}
              className="mt-5 flex w-full items-center justify-center gap-2 rounded-full bg-primary-500 px-5 py-3 text-base font-semibold text-white disabled:opacity-60"
            >
              {busy ? <CircleNotch className="size-5 animate-spin" /> : null}
              {busy ? "Waking the teacher…" : "Start my lesson"}
            </button>
            <p className="mt-3 text-center text-xs text-neutral-500">One free lesson per visitor per day. The lesson ends on its own after {minutes} minutes.</p>
          </section>
        )}
      </div>
    </main>
  );
}
