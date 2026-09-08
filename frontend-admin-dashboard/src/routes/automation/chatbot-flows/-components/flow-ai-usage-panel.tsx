import { useEffect, useState } from 'react';
import { Lightning, Warning, ArrowClockwise } from '@phosphor-icons/react';
import {
    fetchChatbotFlowAiUsage,
    fetchChatbotFlowAiLogs,
    FlowAiUsageSummary,
    FlowAiUsageLogRow,
} from '../-services/chatbot-flow-api';

const DAY_MS = 24 * 60 * 60 * 1000;
const WINDOWS = [
    { label: '7 days', days: 7 },
    { label: '30 days', days: 30 },
    { label: '90 days', days: 90 },
];

const fmtCredits = (n: number) => (Number.isFinite(n) ? n.toFixed(2) : '0.00');
const fmtWhen = (ms: number | null) => (ms ? new Date(ms).toLocaleString() : '—');

/**
 * AI credit consumption of the chatbot flows' AI_RESPONSE nodes.
 *
 * Every AI reply is charged to the institute's AI credits, the same wallet the rest
 * of the platform's AI features draw on, so this reads the shared credit ledger
 * rather than anything chatbot-specific. When the balance runs out the engine stops
 * calling the model and hands those conversations to a human — the banner says so,
 * because from the admin's side the bot simply going quiet is otherwise a mystery.
 */
export function FlowAiUsagePanel({ flowId }: { flowId?: string }) {
    const [days, setDays] = useState(30);
    const [summary, setSummary] = useState<FlowAiUsageSummary | null>(null);
    const [logs, setLogs] = useState<FlowAiUsageLogRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);

    const load = async (windowDays: number) => {
        setLoading(true);
        setFailed(false);
        const startDate = Date.now() - windowDays * DAY_MS;
        try {
            const [usage, page] = await Promise.all([
                fetchChatbotFlowAiUsage(startDate),
                fetchChatbotFlowAiLogs(flowId, 0, 20, startDate),
            ]);
            setSummary(usage);
            setLogs(page.content ?? []);
        } catch {
            // Usage reporting is never worth breaking the flows screen over.
            setFailed(true);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load(days);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [days, flowId]);

    if (loading && !summary) {
        return <div className="py-8 text-center text-sm text-gray-400">Loading AI usage…</div>;
    }

    if (failed) {
        return (
            <div className="py-8 text-center text-sm text-gray-400">
                Couldn&apos;t load AI usage right now.
                <button onClick={() => load(days)} className="ml-2 text-blue-600 hover:underline">
                    Retry
                </button>
            </div>
        );
    }

    const rows = flowId ? (summary?.byFlow ?? []).filter((r) => r.flowId === flowId) : (summary?.byFlow ?? []);

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm font-medium text-gray-700">
                    <Lightning size={16} className="text-amber-500" />
                    AI credit usage
                </div>
                <div className="flex items-center gap-1">
                    {WINDOWS.map((w) => (
                        <button
                            key={w.days}
                            onClick={() => setDays(w.days)}
                            className={`rounded px-2 py-1 text-xs ${
                                days === w.days
                                    ? 'bg-blue-50 font-medium text-blue-700'
                                    : 'text-gray-500 hover:bg-gray-100'
                            }`}
                        >
                            {w.label}
                        </button>
                    ))}
                    <button
                        onClick={() => load(days)}
                        className="rounded p-1 text-gray-400 hover:bg-gray-100"
                        title="Refresh"
                    >
                        <ArrowClockwise size={14} />
                    </button>
                </div>
            </div>

            {summary && !summary.aiEnabled && (
                <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3">
                    <Warning size={18} className="mt-0.5 shrink-0 text-amber-600" />
                    <div className="text-sm text-amber-800">
                        <p className="font-medium">AI replies are paused — no AI credits left.</p>
                        <p className="mt-0.5 text-amber-700">
                            Flows keep running, but AI Reply steps stop calling the model and hand
                            those conversations to a human in the WhatsApp Inbox. Top up AI credits
                            to switch them back on.
                        </p>
                    </div>
                </div>
            )}

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <Stat label="Credits used" value={fmtCredits(summary?.totalCredits ?? 0)} />
                <Stat label="AI replies" value={String(summary?.turnCount ?? 0)} />
                <Stat label="People replied to" value={String(summary?.userCount ?? 0)} />
                <Stat
                    label="Balance"
                    value={
                        summary?.currentBalance != null ? fmtCredits(summary.currentBalance) : '—'
                    }
                    warn={summary != null && !summary.aiEnabled}
                />
            </div>

            {!flowId && rows.length > 0 && (
                <div className="overflow-hidden rounded-lg border">
                    <table className="w-full text-sm">
                        <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                            <tr>
                                <th className="px-3 py-2 text-left font-medium">Flow</th>
                                <th className="px-3 py-2 text-right font-medium">Credits</th>
                                <th className="px-3 py-2 text-right font-medium">Replies</th>
                                <th className="px-3 py-2 text-right font-medium">People</th>
                                <th className="px-3 py-2 text-right font-medium">Last used</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y">
                            {rows.map((r) => (
                                <tr key={r.flowId ?? 'unattributed'}>
                                    <td className="px-3 py-2 text-gray-800">
                                        {r.flowName ?? r.flowId ?? 'Unattributed'}
                                    </td>
                                    <td className="px-3 py-2 text-right tabular-nums text-gray-800">
                                        {fmtCredits(r.totalCredits)}
                                    </td>
                                    <td className="px-3 py-2 text-right tabular-nums text-gray-600">
                                        {r.turnCount}
                                    </td>
                                    <td className="px-3 py-2 text-right tabular-nums text-gray-600">
                                        {r.userCount}
                                    </td>
                                    <td className="px-3 py-2 text-right text-xs text-gray-500">
                                        {fmtWhen(r.lastUsedAt)}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            <div>
                <p className="mb-2 text-xs font-medium uppercase text-gray-500">Recent AI replies</p>
                {logs.length === 0 ? (
                    <p className="py-6 text-center text-sm text-gray-400">
                        No AI replies charged in this window.
                    </p>
                ) : (
                    <div className="overflow-hidden rounded-lg border">
                        <table className="w-full text-sm">
                            <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                                <tr>
                                    <th className="px-3 py-2 text-left font-medium">When</th>
                                    <th className="px-3 py-2 text-left font-medium">Replied to</th>
                                    <th className="px-3 py-2 text-left font-medium">Model</th>
                                    <th className="px-3 py-2 text-right font-medium">Credits</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y">
                                {logs.map((l) => (
                                    <tr key={l.id}>
                                        <td className="whitespace-nowrap px-3 py-2 text-xs text-gray-500">
                                            {fmtWhen(l.createdAt)}
                                        </td>
                                        <td className="px-3 py-2 text-gray-800">
                                            {l.name ?? l.email ?? (
                                                <span className="text-gray-400">Unidentified contact</span>
                                            )}
                                        </td>
                                        <td className="px-3 py-2 text-xs text-gray-500">
                                            {l.model ?? '—'}
                                        </td>
                                        <td className="px-3 py-2 text-right tabular-nums text-gray-800">
                                            {fmtCredits(l.credits)}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}

function Stat({ label, value, warn }: { label: string; value: string; warn?: boolean }) {
    return (
        <div className="rounded-lg border bg-white p-3">
            <p className="text-xs text-gray-500">{label}</p>
            <p
                className={`mt-1 text-lg font-semibold tabular-nums ${
                    warn ? 'text-amber-600' : 'text-gray-800'
                }`}
            >
                {value}
            </p>
        </div>
    );
}
