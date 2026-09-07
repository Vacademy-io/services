import { useEffect, useRef, useState } from "react";
import { Microphone, PaperPlaneRight, SkipForward, ArrowCounterClockwise, Question, SpeakerHigh, SpeakerSlash, Stop, CheckCircle, Circle, XCircle, Fire, Eye, EyeSlash, ArrowsClockwise } from "@phosphor-icons/react";
import { TeacherAvatar } from "./TeacherAvatar";
import type { TutorPace } from "@/hooks/useTutorSocket";

export interface TranscriptLine {
  role: "teacher" | "learner";
  text: string;
  /** What the teacher line is (evaluate, remediate, revisit_verdict, nudge…). */
  kind?: string;
  score?: number | null;
  cleared?: boolean;
  /** The teacher turn this line belongs to (voice mode). */
  turn?: number;
}

export interface LessonStats {
  asked: number;
  correct: number;
  streak: number;
  best: number;
}

const LANGUAGE_LABEL: Record<"en" | "hi", string> = { en: "English", hi: "हिंदी" };

const PACES: Array<{ id: TutorPace; label: string }> = [
  { id: "slower", label: "Slower" },
  { id: "slow", label: "Slow" },
  { id: "normal", label: "Medium" },
  { id: "fast", label: "Fast" },
];

/** A correct / partly / not-yet chip on a verdict line. */
const Verdict: React.FC<{ line: TranscriptLine }> = ({ line }) => {
  const k = line.kind;
  if (k !== "evaluate" && k !== "remediate" && k !== "revisit_verdict") return null;
  const score = typeof line.score === "number" ? line.score : null;
  const ok = k === "evaluate" || (k === "revisit_verdict" && line.cleared);
  const partly = !ok && score !== null && score >= 0.3;
  const cls = ok ? "bg-success-50 text-success-700 border-success-200" : partly ? "bg-warning-50 text-warning-700 border-warning-200" : "bg-neutral-100 text-neutral-600 border-neutral-200";
  const Icon = ok ? CheckCircle : partly ? Circle : XCircle;
  return (
    <span className={`mb-1 inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-semibold ${cls}`}>
      <Icon className="size-3.5" weight="fill" /> {ok ? "Correct" : partly ? "Partly" : "Not yet"}
    </span>
  );
};

export type TutorPhase = "connecting" | "speaking" | "listening" | "thinking" | "idle" | "question" | "media" | "done";

interface TeacherPanelProps {
  teacherName: string;
  teacherAvatarFileId?: string | null;
  phase: TutorPhase;
  transcript: TranscriptLine[];
  check: { prompt: string | null; options: string[]; check_type: string | null; revisit?: boolean; predict?: boolean } | null;
  /** The teacher's speaking pace, chosen by the learner. */
  pace?: TutorPace;
  onPace?: (pace: TutorPace) => void;
  /** Premium teacher avatar: the container the SDK renders into, its state, and a hide/show toggle. */
  avatarContainerRef?: React.RefObject<HTMLDivElement | null>;
  avatarState?: "loading" | "on" | "off" | "failed";
  onToggleAvatar?: () => void;
  /** Why the avatar stopped (vendor error code or message), and a way to start it again. */
  avatarError?: string;
  onRetryAvatar?: () => void;
  /** The lesson language and the ones the course allows switching to. */
  language?: "en" | "hi";
  languages?: Array<"en" | "hi">;
  onLanguage?: (language: "en" | "hi") => void;
  /** Checks asked / answered right in this slide, and the streak. */
  stats?: LessonStats;
  awaiting: "continue" | "answer" | "done" | null;
  voiceMode: boolean;
  micOn: boolean;
  speakOn: boolean;
  onSendText: (text: string) => void;
  onAsk: (text: string) => void;
  onContinue: () => void;
  onControl: (intent: "repeat" | "skip" | "slower" | "faster" | "doubt" | "done") => void;
  onToggleMic: () => void;
  onToggleSpeak: () => void;
  onInterrupt: () => void;
  onEnd: () => void;
  /** Transient server notice (a failed transcription, a slide that cannot be opened). */
  notice?: string | null;
  /** Socket gone: inputs are inert until the learner reconnects. */
  disabled?: boolean;
  /** Phones: the page shows its own teacher strip, so the panel header hides below lg. */
  compact?: boolean;
}

const PHASE_LABEL: Record<TutorPhase, string> = {
  connecting: "Connecting…",
  speaking: "Speaking…",
  listening: "Listening…",
  thinking: "Thinking…",
  idle: "Ready",
  question: "Your turn",
  media: "Watch, then tap Done",
  done: "Slide complete",
};

/** Right rail: the teacher, the conversation, the check, and the controls. */
export const TeacherPanel: React.FC<TeacherPanelProps> = ({
  teacherName, teacherAvatarFileId, phase, transcript, check, awaiting, voiceMode, micOn, speakOn,
  onSendText, onAsk, onContinue, onControl, onToggleMic, onToggleSpeak, onInterrupt, onEnd,
  notice, disabled, compact, pace, onPace, stats, language, languages, onLanguage,
  avatarContainerRef, avatarState, onToggleAvatar, avatarError, onRetryAvatar,
}) => {
  const [text, setText] = useState("");
  const [askMode, setAskMode] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" });
  }, [transcript.length]);

  const submit = () => {
    const t = text.trim();
    if (!t || disabled) return;
    if (askMode || awaiting !== "answer") onAsk(t);
    else onSendText(t);
    setText("");
    setAskMode(false);
  };

  const avatarShown = !!avatarContainerRef && (avatarState === "on" || avatarState === "loading");
  const avatarUsable = !!avatarContainerRef && avatarState !== "failed" && !!onToggleAvatar;

  const Segmented = <T extends string>({ label, items, value, onPick }: { label: string; items: Array<{ id: T; label: string }>; value?: T; onPick: (v: T) => void }) => (
    <div className="flex min-w-0 items-center gap-2" role="group" aria-label={label}>
      <span className="shrink-0 text-xs uppercase tracking-wide text-neutral-500">{label}</span>
      <div className="flex shrink-0 flex-nowrap gap-0.5 rounded-full bg-neutral-100 p-0.5">
        {items.map((it) => (
          <button
            key={it.id}
            type="button"
            onClick={() => onPick(it.id)}
            aria-pressed={value === it.id}
            className={`rounded-full px-2.5 py-0.5 text-xs transition-colors ${value === it.id ? "bg-white font-semibold text-primary-500 shadow-sm" : "text-neutral-600 hover:text-neutral-900"}`}
          >
            {it.label}
          </button>
        ))}
      </div>
    </div>
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      {/* The teacher: an animated avatar card when it is on, otherwise the photo row. */}
      {avatarContainerRef && (
        <div className={`relative overflow-hidden rounded-2xl bg-neutral-900 ${avatarShown ? "aspect-[4/3] w-full" : "h-0"}`}>
          <div ref={avatarContainerRef} className="size-full" aria-label={`${teacherName}'s avatar`} />
          {avatarShown && (
            <>
              <div className="pointer-events-none absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-neutral-900/80 to-transparent" />
              <div className="absolute inset-x-3 bottom-2.5 flex items-end justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-white">{teacherName}</p>
                  <p className="flex items-center gap-1.5 text-xs text-neutral-200">
                    {phase === "speaking" && <span className="inline-block size-1.5 animate-pulse rounded-full bg-success-500" />}
                    {avatarState === "loading" ? "Loading your teacher…" : PHASE_LABEL[phase]}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  {phase === "speaking" && (
                    <button type="button" onClick={onInterrupt} className="rounded-full bg-white/15 p-1.5 text-white backdrop-blur hover:bg-white/25" title="Stop">
                      <Stop className="size-4" weight="fill" />
                    </button>
                  )}
                  {voiceMode && (
                    <button type="button" onClick={onToggleSpeak} className="rounded-full bg-white/15 p-1.5 text-white backdrop-blur hover:bg-white/25" title={speakOn ? "Mute teacher" : "Unmute teacher"}>
                      {speakOn ? <SpeakerHigh className="size-4" /> : <SpeakerSlash className="size-4" />}
                    </button>
                  )}
                  {avatarUsable && avatarState === "on" && (
                    <button type="button" onClick={onToggleAvatar} className="rounded-full bg-white/15 p-1.5 text-white backdrop-blur hover:bg-white/25" title="Hide the teacher">
                      <EyeSlash className="size-4" />
                    </button>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
      )}
      {!avatarShown && (
        <div className={`flex items-center gap-3 border-b border-neutral-200 pb-3 ${compact ? "hidden lg:flex" : ""}`}>
          <TeacherAvatar fileId={teacherAvatarFileId} name={teacherName} speaking={phase === "speaking"} className="size-12" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-neutral-900">{teacherName}</p>
            <p className="text-xs text-neutral-500">{PHASE_LABEL[phase]}</p>
          </div>
          {avatarUsable && avatarState === "off" && (
            <button type="button" onClick={onToggleAvatar} className="rounded-full p-2 text-neutral-500 hover:bg-neutral-100" title="Show the teacher">
              <Eye className="size-4" />
            </button>
          )}
          {avatarState === "failed" && onRetryAvatar && (
            <button type="button" onClick={onRetryAvatar} className="rounded-full p-2 text-neutral-500 hover:bg-neutral-100" title={`Restart the teacher avatar${avatarError ? ` (${avatarError})` : ""}`}>
              <ArrowsClockwise className="size-4" />
            </button>
          )}
          {voiceMode && (
            <button type="button" onClick={onToggleSpeak} className="rounded-full p-2 text-neutral-500 hover:bg-neutral-100" title={speakOn ? "Mute teacher" : "Unmute teacher"}>
              {speakOn ? <SpeakerHigh className="size-4" /> : <SpeakerSlash className="size-4" />}
            </button>
          )}
          {phase === "speaking" && (
            <button type="button" onClick={onInterrupt} className="rounded-full p-2 text-neutral-500 hover:bg-neutral-100" title="Stop">
              <Stop className="size-4" />
            </button>
          )}
        </div>
      )}

      {/* Progress and the learner's own dials, in one quiet strip. */}
      {((stats && stats.asked > 0) || (onLanguage && (languages?.length ?? 0) > 1) || (voiceMode && onPace)) && (
        <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1.5">
          {stats && stats.asked > 0 && (
            <div className="flex items-center gap-1.5 text-xs">
              <span className="rounded-full bg-neutral-100 px-2 py-0.5 font-medium text-neutral-800">{stats.correct}/{stats.asked} right</span>
              {stats.streak >= 2 && (
                <span className="inline-flex items-center gap-1 rounded-full bg-warning-50 px-2 py-0.5 font-medium text-warning-700">
                  <Fire className="size-3.5" weight="fill" /> {stats.streak}
                </span>
              )}
            </div>
          )}
          {onLanguage && (languages?.length ?? 0) > 1 && (
            <Segmented label="Language" items={(languages ?? []).map((l) => ({ id: l, label: LANGUAGE_LABEL[l] }))} value={language} onPick={onLanguage} />
          )}
          {voiceMode && onPace && <Segmented label="Pace" items={PACES} value={pace} onPick={onPace} />}
        </div>
      )}

      {avatarError && (avatarState === "failed" || avatarState === "on") && (
        <p role="status" className="mt-2 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-1.5 text-xs text-neutral-600">
          {avatarState === "failed" ? "Teacher avatar stopped: " : "Teacher avatar hiccup: "}
          <span className="font-mono">{avatarError}</span>
          {avatarState === "failed" && onRetryAvatar && (
            <button type="button" onClick={onRetryAvatar} className="ms-2 font-medium text-primary-500 hover:underline">Try again</button>
          )}
        </p>
      )}
      {notice && (
        <p role="status" className="mt-2 rounded-lg border border-warning-200 bg-warning-50 px-3 py-1.5 text-xs text-warning-700">
          {notice}
        </p>
      )}

      <div ref={listRef} className="min-h-0 flex-1 space-y-2 overflow-y-auto py-3">
        {transcript.map((m, i) => (
          <div key={i} className={`flex ${m.role === "learner" ? "justify-end" : "justify-start"}`}>
            <div className={`max-w-md rounded-2xl px-3 py-2 text-sm ${m.role === "learner" ? "bg-primary-500 text-white" : m.kind === "nudge" ? "border border-warning-200 bg-warning-50 text-neutral-800" : "bg-neutral-100 text-neutral-800"}`}>
              {m.role === "teacher" && (m.kind === "evaluate" || m.kind === "remediate" || m.kind === "revisit_verdict") && (
                <div><Verdict line={m} /></div>
              )}
              {m.text}
            </div>
          </div>
        ))}
        {check && awaiting === "answer" && (
          <div className="rounded-xl border border-primary-200 bg-primary-50 p-3">
            <p className="text-xs font-semibold uppercase text-primary-500">{check.predict ? "Your guess" : check.revisit ? "Quick revisit · one try" : "Question"}</p>
            <p className="mt-1 text-sm text-neutral-900">{check.prompt}</p>
            {check.options.length > 0 && (
              <div className="mt-2 flex flex-col gap-1">
                {check.options.map((o, i) => (
                  <button key={i} type="button" onClick={() => onSendText(o)} className="rounded-lg border border-neutral-200 bg-white px-3 py-1.5 text-start text-sm hover:border-primary-300">
                    {o}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <div className={`space-y-2 border-t border-neutral-200 pt-3 ${disabled ? "pointer-events-none opacity-50" : ""}`}>
        <div className="flex flex-wrap gap-1">
          {awaiting === "continue" && (
            <button
              type="button"
              onClick={onContinue}
              className={`rounded-full bg-primary-500 px-3 py-1 text-xs font-medium text-white ${voiceMode ? "" : "animate-pulse ring-2 ring-primary-200"}`}
            >
              {voiceMode ? "Continue now" : "Continue"}
            </button>
          )}
          {awaiting === "done" && (
            <button type="button" onClick={() => onControl("done")} className="rounded-full bg-primary-500 px-3 py-1 text-xs font-medium text-white">I'm done</button>
          )}
          <button type="button" onClick={() => onControl("repeat")} className="inline-flex items-center gap-1 rounded-full border border-neutral-200 px-3 py-1 text-xs text-neutral-700 hover:bg-neutral-50"><ArrowCounterClockwise className="size-3" /> Repeat</button>
          <button type="button" onClick={() => setAskMode((v) => !v)} className={`inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs ${askMode ? "border-primary-500 bg-primary-50 text-primary-500" : "border-neutral-200 text-neutral-700 hover:bg-neutral-50"}`}><Question className="size-3" /> Doubt</button>
          <button type="button" onClick={() => onControl("skip")} className="inline-flex items-center gap-1 rounded-full border border-neutral-200 px-3 py-1 text-xs text-neutral-700 hover:bg-neutral-50"><SkipForward className="size-3" /> Skip</button>
          <button type="button" onClick={onEnd} className="ms-auto rounded-full px-3 py-1 text-xs text-neutral-500 hover:bg-neutral-100 hover:text-danger-600">End lesson</button>
        </div>
        {voiceMode && (
          <button
            type="button"
            onClick={onToggleMic}
            disabled={phase === "thinking" || phase === "connecting"}
            aria-pressed={micOn}
            className={`flex w-full items-center justify-center gap-2 rounded-full px-4 py-3 text-sm font-semibold transition-colors disabled:opacity-50 ${
              micOn ? "bg-danger-500 text-white animate-pulse" : "bg-primary-500 text-white hover:bg-primary-400"
            }`}
          >
            <Microphone className="size-5" weight="fill" />
            {micOn
              ? "Listening… tap when you're done"
              : awaiting === "answer"
                ? "Tap to answer"
                : "Tap to speak"}
          </button>
        )}
        <div className="flex items-center gap-2">
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") submit();
            }}
            placeholder={askMode ? "Ask your doubt…" : awaiting === "answer" ? (check?.predict ? "Take a guess…" : "Type your answer…") : "Say something or ask…"}
            className="min-w-0 flex-1 rounded-full border border-neutral-200 px-4 py-2 text-sm outline-none focus:border-primary-400"
          />
          <button type="button" onClick={submit} className="rounded-full bg-primary-500 p-2 text-white" title="Send">
            <PaperPlaneRight className="size-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
