import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { getChatUser } from '@/services/chat/getChatUser';
import { UploadFileInS3, getPublicUrl } from '@/services/upload_file';
import type { WhatsAppMediaKind } from '../-services/inbox-api';

/**
 * Meta's ceilings for a free-form media message. Checked before upload so an oversized file is
 * refused here rather than after a round trip that ends in an opaque provider error.
 */
const MEDIA_LIMITS: Record<WhatsAppMediaKind, number> = {
    image: 5 * 1024 * 1024,
    video: 16 * 1024 * 1024,
    audio: 16 * 1024 * 1024,
    document: 100 * 1024 * 1024,
};

/** The formats WhatsApp renders natively. Anything else has to travel as a document. */
const NATIVE_FORMATS: Record<Exclude<WhatsAppMediaKind, 'document'>, string[]> = {
    image: ['image/jpeg', 'image/png'],
    video: ['video/mp4', 'video/3gpp'],
    audio: ['audio/aac', 'audio/mpeg', 'audio/mp4', 'audio/amr', 'audio/ogg'],
};

export interface Attachment {
    url: string;
    name: string;
    kind: WhatsAppMediaKind;
    size: number;
    /** Set when the file was downgraded to a document because WhatsApp cannot render its format. */
    downgradedFrom?: string;
}

export function formatSize(bytes: number): string {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDuration(seconds: number): string {
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/**
 * Which WhatsApp message type a file should be sent as.
 *
 * A HEIC photo, a .mov clip and a FLAC track are all files an admin will realistically pick, and
 * WhatsApp refuses every one of them as its native type — so they go as documents instead, which
 * always delivers. The caller tells the admin when that happens rather than silently changing what
 * they asked for.
 */
function classifyAttachment(file: File): { kind: WhatsAppMediaKind; downgradedFrom?: string } {
    const mime = (file.type || '').toLowerCase();
    const family = (Object.keys(NATIVE_FORMATS) as Array<keyof typeof NATIVE_FORMATS>).find((k) =>
        mime.startsWith(`${k}/`)
    );

    if (!family) return { kind: 'document' };
    return NATIVE_FORMATS[family].includes(mime)
        ? { kind: family }
        : { kind: 'document', downgradedFrom: family };
}

/**
 * A recording format WhatsApp will actually play, or null if this browser can only produce one it
 * rejects.
 *
 * This is the whole difficulty with voice notes: MediaRecorder's universal format is WebM/Opus, and
 * WhatsApp accepts neither WebM nor any container it does not name. MP4/AAC and Ogg/Opus are the
 * two it does accept that browsers can also record, so we take whichever is on offer and refuse
 * rather than send a file that comes back as an opaque provider error.
 */
function pickRecordingFormat(): { mime: string; ext: string } | null {
    if (typeof MediaRecorder === 'undefined') return null;
    const candidates = [
        { mime: 'audio/mp4', ext: 'm4a' },
        { mime: 'audio/mp4;codecs=mp4a.40.2', ext: 'm4a' },
        { mime: 'audio/ogg;codecs=opus', ext: 'ogg' },
        { mime: 'audio/ogg', ext: 'ogg' },
    ];
    return candidates.find((c) => MediaRecorder.isTypeSupported(c.mime)) ?? null;
}

interface Options {
    instituteId: string;
    /**
     * Whether the backend accepts attachments at all. Only 'yes' is permission: 'unknown' means the
     * probe never answered, and `/inbox/send` ignores fields it does not know — so treating that as
     * a yes would deliver the caption alone with the file dropped and nobody told.
     */
    mediaSupport: 'unknown' | 'yes' | 'no';
}

/**
 * Everything the composer needs to attach a file or record a voice note: validation against Meta's
 * rules, upload to a public URL, and the MediaRecorder lifecycle.
 */
export function useAttachment({ instituteId, mediaSupport }: Options) {
    const [attachment, setAttachment] = useState<Attachment | null>(null);
    const [uploading, setUploading] = useState(false);
    const [recording, setRecording] = useState(false);
    const [recordedSeconds, setRecordedSeconds] = useState(0);

    const fileInputRef = useRef<HTMLInputElement>(null);
    const recorderRef = useRef<MediaRecorder | null>(null);
    const chunksRef = useRef<Blob[]>([]);
    const tickRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const discardRef = useRef(false);

    const unsupportedMessage = useCallback(
        (what: string) =>
            mediaSupport === 'no'
                ? `${what} need the updated notification service. The feature is built but not deployed yet.`
                : `Still checking whether this backend accepts attachments — try again in a moment.`,
        [mediaSupport]
    );

    /** Validate, upload and stage one file — shared by the file picker and the voice recorder. */
    const stageFile = useCallback(
        async (file: File) => {
            const { kind, downgradedFrom } = classifyAttachment(file);
            const limit = MEDIA_LIMITS[kind];
            if (file.size > limit) {
                toast.error(
                    `${file.name} is ${formatSize(file.size)}. WhatsApp allows ${formatSize(limit)} for ${kind === 'document' ? 'documents' : `${kind}s`}.`
                );
                return;
            }

            setUploading(true);
            try {
                const { userId } = getChatUser();
                const fileId = await UploadFileInS3(
                    file,
                    setUploading,
                    userId,
                    'WHATSAPP_INBOX',
                    instituteId,
                    true
                );
                if (!fileId) {
                    toast.error('Upload failed. Please try again.');
                    return;
                }
                // WhatsApp fetches the file itself, so this has to be a public URL, not a file id.
                const url = await getPublicUrl(fileId);
                if (!url) {
                    toast.error('Could not get a public link for the uploaded file.');
                    return;
                }
                setAttachment({ url, name: file.name, kind, size: file.size, downgradedFrom });
                if (downgradedFrom) {
                    toast.info(
                        `WhatsApp can't show ${file.type || 'this format'} as ${downgradedFrom === 'image' ? 'a photo' : downgradedFrom}, so it will be sent as a document.`
                    );
                }
            } catch (err) {
                console.error(err);
                toast.error('Upload failed. Please try again.');
            } finally {
                setUploading(false);
            }
        },
        [instituteId]
    );

    /**
     * A greyed-out button that does nothing when clicked reads as a bug, so an unavailable backend
     * says so out loud instead of swallowing the click.
     */
    const openFilePicker = useCallback(() => {
        if (mediaSupport !== 'yes') {
            toast.error(unsupportedMessage('Attachments'));
            return;
        }
        fileInputRef.current?.click();
    }, [mediaSupport, unsupportedMessage]);

    const handleFilePick = useCallback(
        async (e: React.ChangeEvent<HTMLInputElement>) => {
            const file = e.target.files?.[0];
            // Reset immediately so picking the same file twice still fires a change event.
            e.target.value = '';
            if (file) await stageFile(file);
        },
        [stageFile]
    );

    /** Release the mic and the timer. Safe to call twice. */
    const teardownRecorder = useCallback(() => {
        if (tickRef.current) {
            clearInterval(tickRef.current);
            tickRef.current = null;
        }
        recorderRef.current?.stream.getTracks().forEach((t) => t.stop());
        recorderRef.current = null;
        setRecording(false);
        setRecordedSeconds(0);
    }, []);

    // Never leave the microphone open because the admin navigated away mid-recording.
    useEffect(() => teardownRecorder, [teardownRecorder]);

    const handleRecordingStopped = useCallback(
        async (format: { mime: string; ext: string }) => {
            const chunks = chunksRef.current;
            chunksRef.current = [];
            const discarded = discardRef.current;
            teardownRecorder();
            if (discarded || chunks.length === 0) return;

            const blob = new Blob(chunks, { type: format.mime });
            const file = new File([blob], `voice-note-${Date.now()}.${format.ext}`, {
                // The bare mime without codec parameters — classifyAttachment matches on it.
                type: format.mime.split(';')[0],
            });
            await stageFile(file);
        },
        [stageFile, teardownRecorder]
    );

    const startRecording = useCallback(async () => {
        if (mediaSupport !== 'yes') {
            toast.error(unsupportedMessage('Voice notes'));
            return;
        }
        const format = pickRecordingFormat();
        if (!format) {
            toast.error(
                'This browser can only record WebM audio, which WhatsApp rejects. Try Chrome 126+, Safari or Firefox.'
            );
            return;
        }

        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const recorder = new MediaRecorder(stream, { mimeType: format.mime });
            chunksRef.current = [];
            discardRef.current = false;

            recorder.ondataavailable = (e) => {
                if (e.data.size > 0) chunksRef.current.push(e.data);
            };
            recorder.onstop = () => void handleRecordingStopped(format);

            recorderRef.current = recorder;
            recorder.start();
            setRecording(true);
            setRecordedSeconds(0);
            tickRef.current = setInterval(() => setRecordedSeconds((n) => n + 1), 1000);
        } catch (err) {
            console.error(err);
            toast.error('Could not access the microphone. Check the browser permission.');
        }
    }, [mediaSupport, unsupportedMessage, handleRecordingStopped]);

    const stopRecording = useCallback((discard: boolean) => {
        discardRef.current = discard;
        const recorder = recorderRef.current;
        if (recorder && recorder.state !== 'inactive') {
            recorder.stop(); // onstop uploads or discards, then tears down
        }
    }, []);

    return {
        attachment,
        clearAttachment: () => setAttachment(null),
        uploading,
        recording,
        recordedSeconds,
        fileInputRef,
        openFilePicker,
        handleFilePick,
        startRecording,
        stopRecording,
        unsupportedMessage,
    };
}
