import { useEffect, useCallback, useRef } from 'react';
import { useNavigate, useSearch } from '@tanstack/react-router';
import { getInstituteId } from '@/constants/helper';
import { useInboxStore } from '../-stores/inbox-store';
import { getConversations, getMessages, searchConversations } from '../-services/inbox-api';
import { ConversationList } from './conversation-list';
import { ChatPanel } from './chat-panel';
import { describeApiError } from '../-utils/whatsapp-errors';
import { ArrowClockwise } from '@phosphor-icons/react';

const POLL_INTERVAL = 20000; // 20 seconds

export function InboxPage() {
    const instituteId = getInstituteId() || '';
    const { phone: phoneInUrl } = useSearch({ from: '/communication/inbox/' });
    const navigate = useNavigate({ from: '/communication/inbox/' });
    const {
        selectedPhone,
        searchQuery,
        filter,
        setConversations,
        appendConversations,
        setMessages,
        setIsLoadingConversations,
        setIsLoadingMessages,
        setHasMoreConversations,
        setHasMoreMessages,
        setConversationsError,
        setMessagesError,
        conversationOffset,
        incrementOffset,
        resetOffset,
    } = useInboxStore();

    const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

    // The open conversation and ?phone= are kept in step in both directions, each guarded by an
    // equality check so they settle instead of bouncing off each other. URL -> store covers the
    // first paint after a refresh, a pasted link and the browser's back button; store -> URL covers
    // clicking a row. Replacing rather than pushing keeps a long browsing session from burying the
    // page the admin arrived from under fifty chat entries.
    const selectedFromUrl = phoneInUrl ?? null;

    useEffect(() => {
        if (selectedFromUrl !== useInboxStore.getState().selectedPhone) {
            useInboxStore.getState().selectPhone(selectedFromUrl);
        }
    }, [selectedFromUrl]);

    useEffect(() => {
        if ((selectedPhone ?? null) !== selectedFromUrl) {
            navigate({
                search: selectedPhone ? { phone: selectedPhone } : {},
                replace: true,
            });
        }
    }, [selectedPhone, selectedFromUrl, navigate]);

    const loadConversations = useCallback(async (reset = false) => {
        setIsLoadingConversations(true);
        try {
            const offset = reset ? 0 : conversationOffset;
            if (reset) resetOffset();

            // Search spans every conversation regardless of the active filter — a name you type
            // should be findable whether or not it is currently waiting on a reply.
            const data = searchQuery
                ? await searchConversations(instituteId, searchQuery)
                : await getConversations(instituteId, offset, 30, filter);

            if (reset || searchQuery) {
                setConversations(data);
            } else {
                appendConversations(data);
            }
            setHasMoreConversations(data.length >= 30);
            setConversationsError(null);
        } catch (err) {
            console.error('Failed to load conversations', err);
            // Shown in the list rather than swallowed: an inbox that silently renders "No
            // conversations yet" on a failed request reads as "nobody has ever written to us".
            setConversationsError(describeApiError(err, 'Could not load conversations'));
        } finally {
            setIsLoadingConversations(false);
        }
    }, [instituteId, searchQuery, filter, conversationOffset]);

    const loadMessages = useCallback(async (phone: string, cursor?: string) => {
        setIsLoadingMessages(true);
        try {
            const data = await getMessages(phone, instituteId, cursor);
            if (cursor) {
                // Loading older messages — prepend (data is newest-first, reverse for display)
                useInboxStore.getState().prependMessages(data.reverse());
            } else {
                // Initial load — reverse for chronological display
                setMessages(data.reverse());
            }
            setHasMoreMessages(data.length >= 50);
            setMessagesError(null);
        } catch (err) {
            console.error('Failed to load messages', err);
            setMessagesError(describeApiError(err, 'Could not load this conversation'));
        } finally {
            setIsLoadingMessages(false);
        }
    }, [instituteId]);

    // Initial load
    useEffect(() => {
        loadConversations(true);
    }, [instituteId, searchQuery, filter]);

    // Load messages when phone selected
    useEffect(() => {
        if (selectedPhone) {
            loadMessages(selectedPhone);
        }
    }, [selectedPhone, loadMessages]);

    // Polling — refresh conversation list only (lightweight).
    // Messages for the selected phone are NOT replaced to preserve scroll position.
    // User clicks "Refresh" or switches phones to get latest messages.
    useEffect(() => {
        pollRef.current = setInterval(() => {
            loadConversations(true);
        }, POLL_INTERVAL);

        return () => {
            if (pollRef.current) clearInterval(pollRef.current);
        };
    }, [loadConversations]);

    const handleRefresh = () => {
        loadConversations(true);
        if (selectedPhone) loadMessages(selectedPhone);
    };

    const handleLoadMore = () => {
        incrementOffset();
        loadConversations(false);
    };

    const handleRetryMessages = () => {
        if (selectedPhone) loadMessages(selectedPhone);
    };

    const handleLoadOlderMessages = () => {
        const messages = useInboxStore.getState().messages;
        if (messages.length > 0) {
            const oldestTimestamp = messages[0]?.timestamp;
            if (selectedPhone && oldestTimestamp) {
                loadMessages(selectedPhone, oldestTimestamp);
            }
        }
    };

    // Conversations on the current page the chatbot handed over and nobody has closed. Counting
    // escalations rather than every unanswered chat keeps this an honest number: hand-overs are
    // few, so the page almost always holds all of them, while "unanswered" runs into the hundreds
    // and a page-scoped count of those would read like a total and be wrong.
    const waitingCount = useInboxStore((s) => s.conversations).filter((c) => c.escalationId).length;

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-2 border-b bg-white shrink-0">
                <div>
                    <h2 className="text-lg font-semibold text-gray-800">WhatsApp Inbox</h2>
                    <p className="text-xs text-gray-400">
                        View and reply to WhatsApp conversations
                        {waitingCount > 0 && (
                            <span className="ml-2 rounded-full bg-amber-100 px-1.5 py-px font-medium text-amber-700">
                                {waitingCount} handed over by the bot
                            </span>
                        )}
                    </p>
                </div>
                <button
                    onClick={handleRefresh}
                    className="p-2 rounded hover:bg-gray-100 text-gray-500"
                    title="Refresh"
                >
                    <ArrowClockwise size={18} />
                </button>
            </div>

            {/* Main content: conversation list + chat panel.
                On mobile only one pane is visible at a time (driven by selectedPhone). */}
            <div className="flex flex-1 min-h-0 overflow-hidden">
                <ConversationList
                    onLoadMore={handleLoadMore}
                    onRetry={() => loadConversations(true)}
                />
                <ChatPanel
                    onLoadOlder={handleLoadOlderMessages}
                    onRetry={handleRetryMessages}
                />
            </div>
        </div>
    );
}
