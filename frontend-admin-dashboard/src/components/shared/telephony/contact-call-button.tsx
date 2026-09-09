import { useQuery } from '@tanstack/react-query';
import { Phone } from '@phosphor-icons/react';
import { cn } from '@/lib/utils';
import { getCurrentInstituteId } from '@/lib/auth/instituteUtils';
import { CallPickerPopover } from '@/components/shared/leads/call-picker-popover';
import { usePlaceCall } from '@/components/shared/leads/use-place-call';
import { fetchCallOptions } from '@/components/shared/leads/services/call-options';

/**
 * Click-to-call button for a person who is NOT (necessarily) a CRM lead —
 * an enrolled learner in the students list, the attendance tracker, or an
 * assessment's participants, all of which open the same student side-view.
 *
 * The lead surfaces call by `responseId` (audience_response). Learners have no
 * such row, so this passes `userId` alone and the backend resolves the person,
 * their phone, and — when the learner ALSO came through a form — the lead row to
 * file the call under, so one person never ends up with two call histories.
 *
 * Same picker + live-status toast as the leads Call button: an accidental click
 * costs a popover, not provider credits.
 */
interface ContactCallButtonProps {
    /** The person being called (auth user id). */
    userId?: string | null;
    /**
     * audience_response id, when this row IS a CRM lead. The student side-view is
     * shared with the lead surfaces, and a lead is usually not enrolled — passing
     * it makes the backend take the lead path (phone off the lead row) instead of
     * the learner path, whose institute-enrolment check a lead would fail.
     */
    responseId?: string | null;
    /** Their number — only used to decide whether calling is possible at all;
     *  the backend re-resolves it so we never dial a stale local value. */
    phone?: string | null;
    /** Shown on the post-call disposition sheet when the call files onto a lead. */
    name?: string | null;
    className?: string;
}

/**
 * Is calling usable for this institute right now? The options endpoint is the
 * honest signal — it 4xx's unless a provider is configured AND enabled, which is
 * exactly the condition under which a Call button would work. Cached per
 * institute so the many rows/panels that mount this share one request, and a
 * failure simply hides the button rather than showing one that always errors.
 */
function useTelephonyEnabled(instituteId: string): boolean {
    const query = useQuery({
        queryKey: ['telephony-enabled', instituteId],
        queryFn: () => fetchCallOptions(instituteId),
        enabled: !!instituteId,
        staleTime: 10 * 60 * 1000,
        retry: false,
    });
    return query.isSuccess;
}

export function ContactCallButton({
    userId,
    responseId,
    phone,
    name,
    className,
}: ContactCallButtonProps) {
    const instituteId = getCurrentInstituteId() ?? '';
    const telephonyEnabled = useTelephonyEnabled(instituteId);
    const placeCall = usePlaceCall();

    const hasPhone = !!phone && phone.trim() !== '' && phone.trim() !== '-';
    if (!telephonyEnabled || (!userId && !responseId) || !hasPhone) return null;

    const disabled = placeCall.isPending;

    return (
        <CallPickerPopover
            leadUserId={userId}
            disabled={disabled}
            disabledReason={disabled ? 'A call is already being placed' : undefined}
            onConfirm={(preferredNumberId) =>
                placeCall.mutate({
                    responseId: responseId ?? undefined,
                    userId: userId ?? undefined,
                    preferredNumberId: preferredNumberId || undefined,
                    leadName: name ?? undefined,
                })
            }
            trigger={
                <button
                    type="button"
                    onClick={(e) => e.stopPropagation()}
                    disabled={disabled}
                    title="Call"
                    aria-label="Call"
                    className={cn(
                        'inline-flex size-6 shrink-0 items-center justify-center rounded text-muted-foreground transition-colors',
                        disabled
                            ? 'cursor-not-allowed opacity-50'
                            : 'hover:bg-success-50 hover:text-success-600',
                        className
                    )}
                >
                    <Phone weight="fill" className="size-3.5" />
                </button>
            }
        />
    );
}
