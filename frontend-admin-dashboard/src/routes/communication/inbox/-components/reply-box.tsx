import { useState, useCallback, useEffect } from 'react';
import {
    PaperPlaneRight,
    FileText,
    Paperclip,
    X,
    CircleNotch,
    Clock,
    Microphone,
    Stop,
} from '@phosphor-icons/react';
import { toast } from 'sonner';
import {
    sendReply,
    getSessionWindow,
    getInboxMediaSupport,
    type SessionWindow,
    type SendReplyOptions,
} from '../-services/inbox-api';
import { useInboxStore } from '../-stores/inbox-store';
import { useAttachment, formatSize, formatDuration } from '../-hooks/use-attachment';
import { getInstituteId } from '@/constants/helper';
import { sendNotification } from '@/services/unified-send-service';
import { listTemplates, type WhatsAppTemplateDTO } from '@/routes/communication/whatsapp-templates/-services/template-api';

interface Props {
    phone: string;
}

export function ReplyBox({ phone }: Props) {
    const [text, setText] = useState('');
    const [sending, setSending] = useState(false);
    const [showTemplates, setShowTemplates] = useState(false);
    const [templates, setTemplates] = useState<WhatsAppTemplateDTO[]>([]);
    const [templateSearch, setTemplateSearch] = useState('');
    const appendMessage = useInboxStore((s) => s.appendMessage);
    const updateConversationLastMessage = useInboxStore((s) => s.updateConversationLastMessage);
    const markConversationAnswered = useInboxStore((s) => s.markConversationAnswered);
    const instituteId = getInstituteId() || '';

    const [window24h, setWindow24h] = useState<SessionWindow | null>(null);
    const [mediaSupport, setMediaSupport] = useState(getInboxMediaSupport());

    const {
        attachment,
        clearAttachment,
        uploading,
        recording,
        recordedSeconds,
        fileInputRef,
        openFilePicker,
        handleFilePick,
        startRecording,
        stopRecording,
    } = useAttachment({ instituteId, mediaSupport });

    // How much of Meta's 24-hour reply window is left. Null when the backend does not report it,
    // in which case the composer behaves exactly as it always has.
    useEffect(() => {
        let cancelled = false;
        setWindow24h(null);
        clearAttachment();
        getSessionWindow(phone, instituteId).then((w) => {
            if (cancelled) return;
            setWindow24h(w);
            setMediaSupport(getInboxMediaSupport());
        });
        return () => {
            cancelled = true;
        };
    }, [phone, instituteId]);

    // Load templates when panel opens
    useEffect(() => {
        if (showTemplates && templates.length === 0) {
            listTemplates(instituteId)
                .then((data) => setTemplates(data.filter((t) => t.status === 'APPROVED')))
                .catch(() => toast.error('Failed to load templates'));
        }
    }, [showTemplates]);

    const handleSend = useCallback(async () => {
        if ((!text.trim() && !attachment) || sending) return;

        if (attachment && mediaSupport !== 'yes') {
            toast.error(
                mediaSupport === 'no'
                    ? 'Attachments need the updated notification service. The feature is built but not deployed yet.'
                    : 'Still checking whether this backend accepts attachments — try again in a moment.'
            );
            return;
        }

        setSending(true);
        try {
            // With an attachment the typed text rides along as the caption.
            const caption = text.trim();
            const options: SendReplyOptions = attachment
                ? {
                      mediaType: attachment.kind,
                      mediaUrl: attachment.url,
                      filename: attachment.name,
                  }
                : {};
            const sent = await sendReply(phone, caption, instituteId, options);
            appendMessage(sent);
            updateConversationLastMessage(
                phone,
                sent.body || caption || attachment?.name || '',
                'OUTGOING'
            );
            // The backend resolved any open hand-over as part of this send; clear the badge now
            // rather than waiting for the next poll.
            markConversationAnswered(phone);
            setText('');
            clearAttachment();
        } catch (err) {
            console.error(err);
            // A refused send is now recorded in the thread as "Not delivered", so point there
            // instead of leaving the admin with only a toast.
            const axiosErr = err as { response?: { data?: { message?: string } } };
            toast.error(
                axiosErr?.response?.data?.message ||
                    'Failed to send message. Session may have expired (24hr window). It is marked as not delivered in the chat.'
            );
        } finally {
            setSending(false);
        }
    }, [
        text,
        attachment,
        clearAttachment,
        mediaSupport,
        phone,
        sending,
        appendMessage,
        updateConversationLastMessage,
        markConversationAnswered,
        instituteId,
    ]);

    const handleSendTemplate = useCallback(async (template: WhatsAppTemplateDTO) => {
        setSending(true);
        try {
            // Count how many {{N}} params the template needs
            const placeholderMatches = (template.bodyText || '').match(/\{\{\d+\}\}/g);
            const paramCount = placeholderMatches ? placeholderMatches.length : 0;

            const variables: Record<string, string> = {};

            if (paramCount > 0) {
                // Build default values from variable names or sample values
                const defaults: string[] = [];
                for (let i = 0; i < paramCount; i++) {
                    const varName = template.bodyVariableNames?.[i] || '';
                    const sampleVal = template.bodySampleValues?.[i] || '';
                    defaults.push(sampleVal || varName || '');
                }

                // Always prompt user for parameter values
                const labels = defaults.map((d, i) => {
                    const name = template.bodyVariableNames?.[i] || `param ${i + 1}`;
                    return `${name}${d ? ` (e.g. ${d})` : ''}`;
                }).join(', ');

                const userInput = prompt(
                    `Template "${template.name}" requires ${paramCount} value(s):\n${labels}\n\nEnter values (comma-separated):`
                );
                if (userInput === null) { setSending(false); return; }

                const parts = userInput.split(',').map(s => s.trim());
                for (let i = 0; i < paramCount; i++) {
                    variables[String(i + 1)] = parts[i] || defaults[i] || '';
                }
            }

            const response = await sendNotification({
                instituteId,
                channel: 'WHATSAPP',
                templateName: template.name,
                languageCode: template.language || 'en',
                recipients: [{ phone, variables }],
                options: { source: 'inbox-template-send' },
            });

            if (response.status === 'COMPLETED' && response.accepted > 0) {
                toast.success(`Template "${template.name}" sent`);
                // Add to message list
                appendMessage({
                    id: Date.now().toString(),
                    body: `[Template: ${template.name}] ${template.bodyText || ''}`,
                    direction: 'OUTGOING',
                    timestamp: new Date().toISOString(),
                    source: 'unified-send',
                });
                updateConversationLastMessage(phone, `[Template] ${template.name}`, 'OUTGOING');
                setShowTemplates(false);
            } else {
                toast.error(`Failed to send template: ${response.results?.[0]?.error || 'Unknown error'}`);
            }
        } catch (err) {
            console.error(err);
            toast.error('Failed to send template');
        } finally {
            setSending(false);
        }
    }, [phone, instituteId, appendMessage, updateConversationLastMessage]);

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    const filteredTemplates = templates.filter(
        (t) => t.name.toLowerCase().includes(templateSearch.toLowerCase()) ||
               (t.bodyText || '').toLowerCase().includes(templateSearch.toLowerCase())
    );

    return (
        <div className="relative shrink-0">
            {/* Template picker dropdown */}
            {showTemplates && (
                <div className="absolute bottom-full left-0 right-0 bg-white border-t shadow-lg max-h-72 flex flex-col">
                    <div className="flex items-center justify-between px-3 py-2 border-b">
                        <span className="text-xs font-semibold text-gray-600">Send Template (outside 24hr window)</span>
                        <button onClick={() => setShowTemplates(false)} className="p-1 hover:bg-gray-100 rounded">
                            <X size={14} />
                        </button>
                    </div>
                    <div className="px-3 py-1.5">
                        <input
                            type="text"
                            value={templateSearch}
                            onChange={(e) => setTemplateSearch(e.target.value)}
                            placeholder="Search templates..."
                            className="w-full px-2 py-1 text-xs border rounded"
                        />
                    </div>
                    <div className="flex-1 overflow-y-auto">
                        {filteredTemplates.length === 0 ? (
                            <p className="text-xs text-gray-400 text-center py-4">No approved templates found</p>
                        ) : (
                            filteredTemplates.map((t) => (
                                <button
                                    key={t.id}
                                    onClick={() => handleSendTemplate(t)}
                                    disabled={sending}
                                    className="w-full text-left px-3 py-2 hover:bg-green-50 border-b border-gray-50 disabled:opacity-50"
                                >
                                    <p className="text-xs font-medium text-gray-800">{t.name}</p>
                                    <p className="text-[10px] text-gray-400 truncate">{t.bodyText}</p>
                                </button>
                            ))
                        )}
                    </div>
                </div>
            )}

            {/* Why a reply will not go through. WhatsApp only accepts free-form messages and
                attachments for 24 hours after the contact's last message; after that an approved
                template is the only thing that reaches them. Shown only when we actually know the
                window has lapsed — an unknown window stays quiet and lets the send be attempted. */}
            {window24h && !window24h.open && !window24h.unknown && (
                <div className="flex items-start gap-2 border-t border-amber-200 bg-amber-50 px-4 py-2">
                    <Clock size={15} className="mt-0.5 shrink-0 text-amber-600" />
                    <p className="text-xs text-amber-800">
                        <span className="font-medium">The 24-hour reply window has closed.</span>{' '}
                        WhatsApp will reject plain messages and attachments until they message you
                        again — send an approved{' '}
                        <button
                            onClick={() => setShowTemplates(true)}
                            className="underline underline-offset-2 hover:text-amber-900"
                        >
                            template
                        </button>{' '}
                        instead.
                    </p>
                </div>
            )}

            {/* Attachment staged for sending. The caption is whatever is in the text box. */}
            {attachment && (
                <div className="flex items-center gap-2 border-t bg-gray-50 px-4 py-2">
                    {attachment.kind === 'image' ? (
                        <img src={attachment.url} alt="" className="size-10 rounded object-cover" />
                    ) : (
                        <span className="flex size-10 items-center justify-center rounded bg-white text-gray-400">
                            <FileText size={18} />
                        </span>
                    )}
                    <div className="min-w-0 flex-1">
                        <p className="truncate text-xs font-medium text-gray-700">
                            {attachment.name}
                        </p>
                        <p className="text-[11px] text-gray-400">
                            {attachment.kind} · {formatSize(attachment.size)}
                            {attachment.downgradedFrom ? ' · sent as a document' : ''}
                            {attachment.kind === 'audio' ? ' · WhatsApp sends audio without a caption' : ''}
                        </p>
                    </div>
                    <button
                        onClick={clearAttachment}
                        className="rounded p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-600"
                        title="Remove attachment"
                    >
                        <X size={14} />
                    </button>
                </div>
            )}

            {/* Recording in progress */}
            {recording && (
                <div className="flex items-center gap-2 border-t bg-red-50 px-4 py-2">
                    <span className="size-2 animate-pulse rounded-full bg-red-500" />
                    <p className="flex-1 text-xs text-red-700">
                        Recording voice note · {formatDuration(recordedSeconds)}
                    </p>
                    <button
                        onClick={() => stopRecording(true)}
                        className="rounded px-2 py-1 text-xs text-red-600 hover:bg-red-100"
                    >
                        Cancel
                    </button>
                </div>
            )}

            {/* Reply bar */}
            <div className="px-4 py-3 bg-white border-t flex items-end gap-2">
                <button
                    onClick={() => setShowTemplates(!showTemplates)}
                    className="p-2 text-gray-400 hover:text-green-600 hover:bg-green-50 rounded-lg shrink-0"
                    title="Send template (for messages outside 24hr window)"
                >
                    <FileText size={20} />
                </button>
                <input
                    ref={fileInputRef}
                    type="file"
                    className="hidden"
                    accept="image/*,video/*,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv"
                    onChange={handleFilePick}
                />
                <button
                    onClick={openFilePicker}
                    disabled={uploading || sending || recording}
                    className={`shrink-0 rounded-lg p-2 hover:bg-green-50 hover:text-green-600 disabled:opacity-40 ${
                        mediaSupport === 'no' ? 'text-gray-300' : 'text-gray-400'
                    }`}
                    title={
                        mediaSupport === 'no'
                            ? 'Attachments need the updated notification service — not deployed yet'
                            : 'Attach a photo, video or document'
                    }
                >
                    {uploading ? (
                        <CircleNotch size={20} className="animate-spin" />
                    ) : (
                        <Paperclip size={20} />
                    )}
                </button>
                <button
                    onClick={recording ? () => stopRecording(false) : startRecording}
                    disabled={uploading || sending}
                    className={`shrink-0 rounded-lg p-2 disabled:opacity-40 ${
                        recording
                            ? 'bg-red-50 text-red-600 hover:bg-red-100'
                            : mediaSupport === 'no'
                              ? 'text-gray-300 hover:bg-green-50 hover:text-green-600'
                              : 'text-gray-400 hover:bg-green-50 hover:text-green-600'
                    }`}
                    title={recording ? 'Stop and attach the recording' : 'Record a voice note'}
                >
                    {recording ? <Stop size={20} weight="fill" /> : <Microphone size={20} />}
                </button>
                <textarea
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder={attachment ? 'Add a caption...' : 'Type a message...'}
                    rows={1}
                    className="flex-1 px-3 py-2 text-sm border rounded-lg resize-none focus:outline-none focus:ring-1 focus:ring-green-400 max-h-24"
                    style={{ minHeight: '40px' }}
                />
                <button
                    onClick={handleSend}
                    disabled={(!text.trim() && !attachment) || sending || uploading}
                    className="p-2.5 bg-green-500 text-white rounded-full hover:bg-green-600 disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
                >
                    <PaperPlaneRight size={18} weight="fill" />
                </button>
            </div>
        </div>
    );
}
