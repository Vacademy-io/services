import { useEffect, useMemo, useRef } from 'react';
import { useInboxStore } from '../-stores/inbox-store';
import type { InboxMessage } from '../-services/inbox-api';
import { ReplyBox } from './reply-box';
import { DeliveryTicks, deliveryState } from './delivery-ticks';
import { formatMessageTime, groupMessagesByDay } from '../-utils/day-labels';
import { explainWhatsAppFailure } from '../-utils/whatsapp-errors';
import {
    ChatCircle,
    User,
    Robot,
    ArrowUp,
    ArrowLeft,
    FileText,
    HandWaving,
    WarningCircle,
    ArrowClockwise,
} from '@phosphor-icons/react';

interface Props {
    onLoadOlder: () => void;
    /** Re-run the message load after it failed. */
    onRetry: () => void;
}

export function ChatPanel({ onLoadOlder, onRetry }: Props) {
    const selectedPhone = useInboxStore((s) => s.selectedPhone);
    const selectPhone = useInboxStore((s) => s.selectPhone);
    const messages = useInboxStore((s) => s.messages);
    const conversations = useInboxStore((s) => s.conversations);
    const isLoading = useInboxStore((s) => s.isLoadingMessages);
    const hasMore = useInboxStore((s) => s.hasMoreMessages);
    const messagesError = useInboxStore((s) => s.messagesError);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const chatContainerRef = useRef<HTMLDivElement>(null);

    const selectedConvo = conversations.find((c) => c.phone === selectedPhone);

    // Day-by-day runs, so the thread carries "Today" / "Yesterday" / "Monday" separators the way
    // WhatsApp does instead of leaving every bubble with only a bare clock time.
    const dayGroups = useMemo(() => groupMessagesByDay(messages), [messages]);

    // Auto-scroll to bottom on new messages
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages.length]);

    if (!selectedPhone) {
        return (
            <div className="flex-1 hidden md:flex items-center justify-center bg-gray-50">
                <div className="text-center text-gray-400">
                    <ChatCircle size={56} className="mx-auto mb-3 opacity-40" />
                    <p className="text-sm font-medium">Select a conversation</p>
                    <p className="text-xs mt-1">Choose a contact from the left to view messages</p>
                </div>
            </div>
        );
    }

    return (
        <div className="flex-1 flex flex-col bg-[#e5ddd5] min-w-0">
            {/* Chat header */}
            <div className="px-4 py-2.5 bg-white border-b flex items-center gap-3 shrink-0">
                <button
                    onClick={() => selectPhone(null)}
                    className="md:hidden p-1 -ml-1 rounded hover:bg-gray-100 text-gray-500 shrink-0"
                    title="Back to conversations"
                >
                    <ArrowLeft size={20} />
                </button>
                <div className="w-9 h-9 rounded-full bg-green-100 flex items-center justify-center shrink-0">
                    <User size={18} className="text-green-700" />
                </div>
                <div className="min-w-0">
                    <p className="text-sm font-semibold text-gray-800 truncate">
                        {selectedConvo?.senderName || selectedPhone}
                    </p>
                    <p className="text-xs text-gray-400 truncate">
                        {selectedConvo?.senderName ? selectedPhone : ''}
                        {selectedConvo?.userId && (
                            <span className="ml-2 text-blue-500">ID: {selectedConvo.userId}</span>
                        )}
                    </p>
                </div>
            </div>

            {/* The chatbot stepped aside on this conversation — say so, and say why, so the
                admin knows what they are answering before scrolling the thread. Keyed on the
                escalation itself: awaitingReply is true for any chat the person spoke last on, and
                this banner has a reason to show only when the bot actually handed over. */}
            {!!selectedConvo?.escalationId && (
                <div className="flex items-start gap-2 border-b border-amber-200 bg-amber-50 px-4 py-2 shrink-0">
                    <HandWaving size={16} className="mt-0.5 shrink-0 text-amber-600" />
                    <div className="min-w-0 text-xs text-amber-800">
                        <p className="font-medium">Waiting for your reply</p>
                        <p className="text-amber-700">{escalationReasonText(selectedConvo.escalationReason)}</p>
                        {selectedConvo.escalationMessage && (
                            <p className="mt-0.5 italic text-amber-700">
                                They asked: “{selectedConvo.escalationMessage}”
                            </p>
                        )}
                    </div>
                </div>
            )}

            {/* Messages area */}
            <div ref={chatContainerRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-2">
                {/* A load that failed says so and offers the retry, rather than leaving an empty
                    thread that reads as "this person never wrote to you". */}
                {messagesError && (
                    <div className="mx-auto max-w-md rounded-lg border border-red-200 bg-red-50 px-3 py-2">
                        <p className="flex items-center gap-1.5 text-xs font-medium text-red-700">
                            <WarningCircle size={14} /> {messagesError.title}
                        </p>
                        {messagesError.detail && (
                            <p className="mt-0.5 text-caption text-red-600">{messagesError.detail}</p>
                        )}
                        <button
                            onClick={onRetry}
                            disabled={isLoading}
                            className="mt-1.5 inline-flex items-center gap-1 rounded-md bg-white px-2 py-1 text-caption font-medium text-red-700 shadow-sm hover:bg-red-100 disabled:opacity-60"
                        >
                            <ArrowClockwise size={12} />
                            {isLoading ? 'Retrying…' : 'Try again'}
                        </button>
                    </div>
                )}

                {/* Load older button */}
                {hasMore && !messagesError && messages.length > 0 && (
                    <div className="text-center py-2">
                        <button
                            onClick={onLoadOlder}
                            disabled={isLoading}
                            className="text-xs px-3 py-1 bg-white rounded-full shadow text-gray-500 hover:bg-gray-50 inline-flex items-center gap-1"
                        >
                            <ArrowUp size={12} />
                            {isLoading ? 'Loading...' : 'Load older messages'}
                        </button>
                    </div>
                )}

                {!isLoading && !messagesError && messages.length === 0 && (
                    <p className="py-10 text-center text-xs text-gray-500">
                        No messages in this conversation yet
                    </p>
                )}

                {dayGroups.map((group) => (
                    <div key={group.key || 'undated'} className="space-y-2">
                        {group.label && (
                            <div className="sticky top-0 z-10 flex justify-center py-1">
                                <span className="rounded-full bg-white/90 px-3 py-0.5 text-caption font-medium uppercase tracking-wide text-gray-500 shadow-sm">
                                    {group.label}
                                </span>
                            </div>
                        )}

                        {group.messages.map((msg, i) => (
                            <MessageBubble key={msg.id || `${group.key}-${i}`} msg={msg} />
                        ))}
                    </div>
                ))}

                <div ref={messagesEndRef} />
            </div>

            {/* Reply box */}
            <ReplyBox phone={selectedPhone} />
        </div>
    );
}

function MessageBubble({ msg }: { msg: InboxMessage }) {
    const failed = msg.deliveryStatus === 'FAILED';
    // The provider's verdict, said in words an admin can act on: "Re-engagement message (131047)"
    // becomes the 24-hour window, and an unrecognised code still shows the provider's exact text.
    const failure = failed ? explainWhatsAppFailure(msg.error) : null;

    return (
        <div className={`flex ${msg.direction === 'OUTGOING' ? 'justify-end' : 'justify-start'}`}>
            <div
                className={`max-w-[65%] px-3 py-2 rounded-lg text-sm shadow-sm ${
                    failed
                        ? 'bg-red-50 border border-red-200 rounded-tr-none'
                        : msg.direction === 'OUTGOING'
                          ? 'bg-[#dcf8c6] rounded-tr-none'
                          : 'bg-white rounded-tl-none'
                }`}
            >
                {/* Sender label */}
                {msg.direction === 'INCOMING' && msg.senderName && (
                    <p className="text-xs font-medium text-green-700 mb-0.5">{msg.senderName}</p>
                )}
                {msg.direction === 'OUTGOING' && (
                    <p className="text-xs font-medium text-blue-600 mb-0.5 flex items-center gap-0.5">
                        <Robot size={10} /> Bot
                    </p>
                )}

                {/* Template header media (image / video / document) actually sent */}
                {msg.headerMediaUrl && (
                    <MessageHeaderMedia type={msg.headerType} url={msg.headerMediaUrl} />
                )}

                {/* A free-form attachment sent from this Inbox (not a template header) */}
                {msg.mediaUrl && (
                    <MessageHeaderMedia
                        type={msg.mediaType}
                        url={msg.mediaUrl}
                        filename={msg.mediaFilename}
                    />
                )}

                {/* Message body — the actual template text the recipient received */}
                {msg.body && (
                    <p className="whitespace-pre-wrap break-words text-gray-800">{msg.body}</p>
                )}

                {/* Template context: which template it came from */}
                {msg.templateName && (
                    <p className="text-caption text-gray-500 mt-1 flex flex-wrap items-center gap-1">
                        <span className="italic">via template “{msg.templateName}”</span>
                        {msg.provider && (
                            <span className="px-1 py-px rounded bg-black/5 uppercase tracking-wide">
                                {msg.provider}
                            </span>
                        )}
                    </p>
                )}

                {/* Why this one never arrived — one block for every kind of send, so a failure is
                    explained the same whether it was a template, a reply or an attachment. */}
                {failed && (
                    <div className="mt-1 rounded-md bg-red-100/70 px-2 py-1 text-caption text-red-700">
                        <p className="flex flex-wrap items-center gap-1 font-medium">
                            <WarningCircle size={11} />
                            Not delivered
                            {msg.attemptedType && msg.attemptedType !== 'text' && (
                                <span className="rounded bg-red-200/70 px-1 py-px uppercase tracking-wide">
                                    {msg.attemptedType}
                                </span>
                            )}
                        </p>
                        {failure && (
                            <p className="mt-0.5 text-red-600">
                                <span className="font-medium">{failure.title}</span>
                                {failure.detail ? ` — ${failure.detail}` : ''}
                            </p>
                        )}
                        {failure?.accountLevel && (
                            <p className="mt-0.5 font-medium text-red-700">
                                This affects every WhatsApp message from this number, not only this chat.
                            </p>
                        )}
                        {failure?.code && (
                            <p className="mt-0.5 text-red-400">WhatsApp error code {failure.code}</p>
                        )}
                    </div>
                )}

                {/* Timestamp + status */}
                <p
                    className={`text-[10px] mt-1 text-right ${
                        msg.direction === 'OUTGOING' ? 'text-gray-500' : 'text-gray-400'
                    }`}
                >
                    {formatMessageTime(msg.timestamp)}
                    {msg.direction === 'OUTGOING' && !failed && (
                        <DeliveryTicks state={deliveryState(msg.deliveryStatus, msg.status)} />
                    )}
                </p>
            </div>
        </div>
    );
}

/** Plain-language version of why the chatbot handed this conversation over. */
function escalationReasonText(reason?: string): string {
    switch (reason) {
        case 'MAX_TURNS':
            return 'The conversation reached its automated reply limit.';
        case 'AI_ERROR':
            return 'The assistant could not generate a reply.';
        case 'MANUAL':
            return 'Handed over by an admin.';
        default:
            return "The assistant didn't have the information to answer, so it said it would check with the team.";
    }
}

/**
 * Renders an attachment on a message — a template's header media, or a free-form image/video/
 * audio/document sent from the Inbox. Accepts either casing: template headers are stored upper
 * case ("IMAGE"), free-form sends lower case ("image").
 */
function MessageHeaderMedia({
    type,
    url,
    filename,
}: {
    type?: string;
    url: string;
    filename?: string;
}) {
    const t = (type || 'IMAGE').toUpperCase();

    if (t === 'VIDEO') {
        return (
            <video
                src={url}
                controls
                className="mb-1.5 max-h-64 w-full rounded-md bg-black/5"
            />
        );
    }

    if (t === 'AUDIO') {
        return <audio src={url} controls className="mb-1.5 w-full" />;
    }

    if (t === 'DOCUMENT') {
        return (
            <a
                href={url}
                target="_blank"
                rel="noopener noreferrer"
                className="mb-1.5 flex items-center gap-1.5 rounded-md bg-black/5 px-2 py-1.5 text-caption text-blue-600 hover:underline"
            >
                <FileText size={14} />
                <span className="truncate">{filename || 'View document'}</span>
            </a>
        );
    }

    // IMAGE (default) — clickable to open full size; hides itself if the URL is dead/expired.
    return (
        <a href={url} target="_blank" rel="noopener noreferrer">
            <img
                src={url}
                alt="attachment"
                loading="lazy"
                onError={(e) => {
                    const anchor = e.currentTarget.closest('a');
                    if (anchor) anchor.style.display = 'none';
                }}
                className="mb-1.5 max-h-64 w-full rounded-md bg-black/5 object-contain"
            />
        </a>
    );
}
