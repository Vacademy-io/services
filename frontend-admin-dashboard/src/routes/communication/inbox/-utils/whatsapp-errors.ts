/**
 * Plain-language explanations for WhatsApp failures.
 *
 * The provider hands us its own vocabulary — "Re-engagement message (131047)", "Business
 * eligibility payment issue (131042)" — which tells an admin nothing about what went wrong or what
 * to do next. This maps the codes we can identify with certainty onto a sentence they can act on,
 * and leaves everything else showing the provider's exact words: a wrong explanation for a real
 * failure is worse than an unexplained one.
 */

export interface FailureExplanation {
    /** Headline: what happened, in five words. */
    title: string;
    /** What to do about it. Absent when the code is one we cannot speak to. */
    detail?: string;
    /** Provider error code, when the message carried one. */
    code?: string;
    /**
     * True when the failure kills every send on this WhatsApp number rather than only this
     * recipient — the difference between "this person can't be reached" and "the account is down".
     */
    accountLevel?: boolean;
}

interface KnownFailure {
    title: string;
    detail: string;
    accountLevel?: boolean;
}

/**
 * WhatsApp Cloud API error codes. Recipient-level entries explain a single undelivered message;
 * account-level entries mean the number itself cannot send until someone fixes it.
 */
const BY_CODE: Record<string, KnownFailure> = {
    // --- Recipient-level: this message, this person ---
    '131047': {
        title: '24-hour reply window closed',
        detail: 'This person last messaged you more than 24 hours ago, so WhatsApp no longer allows a free-form reply. Send an approved template to re-open the conversation.',
    },
    '131026': {
        title: 'Message undeliverable',
        detail: 'WhatsApp could not deliver to this number — it may not be on WhatsApp, may have been typed wrongly, or the person has never accepted messages from this business.',
    },
    '131049': {
        title: 'Held back by WhatsApp',
        detail: 'WhatsApp limits how many marketing messages one person receives. Nothing is broken — try again later, or use a utility template if the message is transactional.',
    },
    '130472': {
        title: 'Excluded by a WhatsApp experiment',
        detail: 'This number is in a Meta experiment group that does not receive marketing messages. Utility and authentication templates are unaffected.',
    },
    '131021': {
        title: 'Cannot message this number',
        detail: 'The recipient is the same as the sender, or the number cannot receive messages from this account.',
    },
    '131051': {
        title: 'Unsupported message type',
        detail: 'WhatsApp does not accept this kind of message on this account.',
    },
    '131052': {
        title: 'Media could not be downloaded',
        detail: 'WhatsApp could not fetch the attachment from its URL. Check the file is still public and re-send.',
    },
    '131053': {
        title: 'Media could not be uploaded',
        detail: 'WhatsApp rejected the attachment — usually the format or the file size. Re-send it as a supported type under the size limit.',
    },

    // --- Account-level: every send on this number is affected ---
    '131042': {
        title: 'Billing problem on the WhatsApp account',
        detail: 'Meta has no working payment method for this WhatsApp Business Account, so every message on this number is being refused. Fix the card in WhatsApp Manager → Billing.',
        accountLevel: true,
    },
    '131031': {
        title: 'WhatsApp account restricted',
        detail: 'Meta has locked or restricted this WhatsApp Business Account, usually for a policy or quality reason. Check the account quality page in WhatsApp Manager.',
        accountLevel: true,
    },
    '131045': {
        title: 'Number not registered correctly',
        detail: 'This WhatsApp number is not fully registered with Meta, or its certificate is wrong. Re-register it in Settings → WhatsApp.',
        accountLevel: true,
    },
    '133010': {
        title: 'Number not registered',
        detail: 'This WhatsApp number has not completed registration with Meta and cannot send anything yet.',
        accountLevel: true,
    },
    '190': {
        title: 'WhatsApp access token expired',
        detail: 'The Meta access token for this institute is no longer valid, so no message can be sent. Update it in Settings → WhatsApp.',
        accountLevel: true,
    },
    '368': {
        title: 'Account temporarily blocked',
        detail: 'Meta has temporarily blocked this account for policy reasons. Sending resumes only once the block expires or is appealed.',
        accountLevel: true,
    },

    // --- Template problems ---
    '132000': {
        title: 'Wrong number of template values',
        detail: 'The template was filled with a different number of values than it has placeholders.',
    },
    '132001': {
        title: 'Template not found',
        detail: 'No approved template with this name and language exists on the account. Sync templates, or pick another one.',
    },
    '132005': {
        title: 'Template text too long',
        detail: 'The filled-in template exceeds the length WhatsApp allows. Shorten the values.',
    },
    '132007': {
        title: 'Template content rejected',
        detail: 'The values broke WhatsApp’s formatting rules — usually a newline, a tab, or four spaces in a row inside a placeholder.',
    },
    '132012': {
        title: 'Template value format mismatch',
        detail: 'One of the values does not match the format the template was approved with.',
    },
    '132015': {
        title: 'Template paused for low quality',
        detail: 'WhatsApp paused this template because recipients marked it as unwanted. Use a different template until it recovers.',
    },
    '132016': {
        title: 'Template disabled',
        detail: 'WhatsApp permanently disabled this template for quality reasons. It cannot be used again.',
    },

    // --- Throttling ---
    '130429': {
        title: 'Rate limit reached',
        detail: 'This account is sending faster than WhatsApp allows right now. The message can be retried shortly.',
    },
    '131056': {
        title: 'Too many messages to this person',
        detail: 'WhatsApp throttled the number of messages between this account and this recipient. Try again later.',
    },
    '80007': {
        title: 'Rate limit reached',
        detail: 'This account has hit its WhatsApp request limit. Wait a few minutes and retry.',
    },
    '4': {
        title: 'Rate limit reached',
        detail: 'This account has hit its WhatsApp request limit. Wait a few minutes and retry.',
    },
};

/**
 * Failures whose provider text carries no usable code. Matched on a lowercased substring of the
 * whole message, so the phrase has to be specific enough that it cannot match anything else.
 */
const BY_TEXT: Array<{ match: string; failure: KnownFailure }> = [
    {
        // WATI answers an exhausted wallet with prose, not a code — a three-day outage once hid
        // behind "unknown error" because of it.
        match: 'insufficient',
        failure: {
            title: 'Out of WhatsApp credits',
            detail: 'The messaging account has run out of credits. Top it up with the provider before sending again.',
            accountLevel: true,
        },
    },
    {
        match: 'out of credit',
        failure: {
            title: 'Out of WhatsApp credits',
            detail: 'The messaging account has run out of credits. Top it up with the provider before sending again.',
            accountLevel: true,
        },
    },
    {
        match: '24 hour',
        failure: {
            title: '24-hour reply window closed',
            detail: 'This person last messaged you more than 24 hours ago, so only an approved template can reach them now.',
        },
    },
];

/** Pulls "131047" out of "Re-engagement message (131047)" — or out of a bare code. */
function extractCode(raw: string): string | undefined {
    const trailing = raw.match(/\((\d{1,7})\)\s*$/);
    if (trailing?.[1]) return trailing[1];
    const labelled = raw.match(/\b(?:code|error)[\s:#=]+(\d{1,7})\b/i);
    if (labelled?.[1]) return labelled[1];
    if (/^\d{1,7}$/.test(raw.trim())) return raw.trim();
    return undefined;
}

/** The provider's own words, with the code stripped off — we render that separately. */
function withoutCode(raw: string): string {
    return raw.replace(/\s*\(\d{1,7}\)\s*$/, '').trim();
}

/** A headline is one line. A provider that answers with an essay gets cut, not laid out in full. */
function truncate(text: string, max = 160): string {
    const flat = text.replace(/\s+/g, ' ').trim();
    return flat.length > max ? `${flat.slice(0, max)}…` : flat;
}

/**
 * Explain a provider failure string. Returns null for a blank input; for an unrecognised one,
 * returns the provider's text as the title with no invented detail.
 */
export function explainWhatsAppFailure(raw?: string | null): FailureExplanation | null {
    if (!raw || !raw.trim()) return null;

    const text = raw.trim();
    const code = extractCode(text);

    const known = code ? BY_CODE[code] : undefined;
    if (known) {
        return { title: known.title, detail: known.detail, code, accountLevel: known.accountLevel };
    }

    const lower = text.toLowerCase();
    const matched = BY_TEXT.find((entry) => lower.includes(entry.match));
    if (matched) {
        return {
            title: matched.failure.title,
            detail: matched.failure.detail,
            code,
            accountLevel: matched.failure.accountLevel,
        };
    }

    const provider = withoutCode(text);
    return { title: provider ? truncate(provider) : 'Not delivered', code };
}

export interface ApiErrorInfo {
    title: string;
    detail?: string;
}

interface AxiosLikeError {
    code?: string;
    message?: string;
    isAxiosError?: boolean;
    response?: {
        status?: number;
        data?: { message?: string; error?: string; detail?: string } | string;
    };
}

/** The server's own explanation, when it sent one, whatever shape it used. */
function serverMessage(err: AxiosLikeError): string | undefined {
    const data = err.response?.data;
    if (typeof data === 'string') {
        // A proxy answering with an HTML error page is not an explanation — fall through to the
        // status-code wording rather than pasting markup into a toast.
        const trimmed = data.trim();
        return !trimmed || trimmed.startsWith('<') ? undefined : trimmed;
    }
    return data?.message || data?.error || data?.detail || undefined;
}

/**
 * Turn a failed request into something worth showing a person: the server's reason where there is
 * one (run through the WhatsApp translation first, since send failures arrive as provider text),
 * and otherwise a description of the transport failure rather than a bare "something went wrong".
 */
export function describeApiError(err: unknown, fallback: string): ApiErrorInfo {
    const error = (err ?? {}) as AxiosLikeError;

    const fromServer = serverMessage(error);
    if (fromServer) {
        const explained = explainWhatsAppFailure(fromServer);
        if (explained) return { title: explained.title, detail: explained.detail };
    }

    const status = error.response?.status;
    if (status === undefined) {
        if (typeof navigator !== 'undefined' && navigator.onLine === false) {
            return { title: 'You are offline', detail: 'Reconnect and try again.' };
        }
        if (error.code === 'ECONNABORTED') {
            return {
                title: 'The request timed out',
                detail: 'The server took too long to answer. Try again.',
            };
        }
        // A request that never reached the network and one that threw while we were handling the
        // answer both land here, and they are not the same problem. Only the first is worth
        // sending someone to check their connection over.
        const transportFailure = error.isAxiosError || error.code !== undefined || !error.message;
        return transportFailure
            ? {
                  title: fallback,
                  detail: 'Could not reach the server. Check your connection and try again.',
              }
            : { title: fallback, detail: `Unexpected error: ${truncate(error.message ?? '')}` };
    }

    if (status === 401 || status === 403) {
        return {
            title: 'You do not have access to this inbox',
            detail: 'Your session may have expired — reload the page and sign in again.',
        };
    }
    if (status === 404) {
        return { title: fallback, detail: 'The server does not have this conversation any more.' };
    }
    if (status === 413) {
        return {
            title: 'That file is too large',
            detail: 'WhatsApp rejects attachments over its size limit.',
        };
    }
    if (status === 429) {
        return { title: 'Too many requests', detail: 'Slow down for a moment, then try again.' };
    }
    if (status >= 500) {
        return {
            title: fallback,
            detail: `The server returned an error (${status}). Try again in a moment.`,
        };
    }

    return { title: fallback, detail: `Request failed with status ${status}.` };
}
