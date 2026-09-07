import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { CircleNotch, UserFocus } from '@phosphor-icons/react';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { MyButton } from '@/components/design-system/button';
import { createTutorAvatar, getTutorAvatarJob } from '@/services/tutor';

interface Props {
    /** The teacher's face photo (media file id) the avatar is built from. */
    fileId?: string;
    inheritedFileId?: string;
    teacherName?: string;
    provider?: 'none' | 'spatius';
    avatarId?: string;
    inheritedAvatarId?: string;
    available: boolean;
    onChange: (provider: 'none' | 'spatius', avatarId: string) => void;
}

const POLL_MS = 5000;

/**
 * Premium teacher avatar (Spatius): an animated likeness built from the
 * face photo, rendered on the learner's device and lip-synced to the
 * teacher's voice in voice lessons. Charged per lesson minute on top of
 * the live minute. Creation is asynchronous; this field polls the job.
 */
export const TeacherAvatarField: React.FC<Props> = ({
    fileId,
    inheritedFileId,
    teacherName,
    provider,
    avatarId,
    inheritedAvatarId,
    available,
    onChange,
}) => {
    const [consent, setConsent] = useState(false);
    const [jobId, setJobId] = useState<string | null>(null);
    const [status, setStatus] = useState<string | null>(null);
    const [manualId, setManualId] = useState('');
    const timer = useRef<ReturnType<typeof setInterval> | null>(null);
    const photo = fileId || inheritedFileId;
    const active = provider === 'spatius' && !!avatarId;
    const inheritedActive = !active && !!inheritedAvatarId && provider !== 'none';

    useEffect(() => {
        if (!jobId) return;
        const tick = async () => {
            try {
                const j = await getTutorAvatarJob(jobId);
                setStatus(j.status);
                if (j.status === 'succeeded' && j.avatar_id) {
                    onChange('spatius', j.avatar_id);
                    setJobId(null);
                    toast.success('Teacher avatar is ready. Save the settings to use it.');
                } else if (j.status === 'failed') {
                    setJobId(null);
                    toast.error(j.error || 'The avatar could not be created from this photo');
                }
            } catch (e: unknown) {
                setJobId(null);
                toast.error(e instanceof Error ? e.message : 'Could not check the avatar job');
            }
        };
        void tick();
        timer.current = setInterval(() => void tick(), POLL_MS);
        return () => {
            if (timer.current) clearInterval(timer.current);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [jobId]);

    const create = async () => {
        if (!photo) return;
        try {
            setStatus('queued');
            const j = await createTutorAvatar(photo, teacherName);
            setJobId(j.job_id);
        } catch (e: unknown) {
            setStatus(null);
            toast.error(e instanceof Error ? e.message : 'Could not start the avatar');
        }
    };

    return (
        <div className="space-y-2 rounded-md border border-neutral-200 bg-neutral-50 p-3">
            <p className="flex items-center gap-2 text-sm font-medium text-neutral-800">
                <UserFocus className="size-4 text-primary-500" />
                Animated teacher avatar (premium)
            </p>
            <p className="text-xs text-neutral-600">
                Builds a lip-synced animated likeness of the teacher from the face photo above. In
                voice lessons learners see the teacher speak; it costs 1 extra credit per lesson
                minute while it is shown, and learners can hide it.
            </p>
            {!available && (
                <p className="text-xs text-neutral-500">
                    Not enabled on this server yet. Ask Vacademy to switch it on for your institute.
                </p>
            )}
            {available && active && (
                <p className="text-xs text-success-700">
                    Avatar ready ({avatarId}). Save the settings to apply it to lessons.
                </p>
            )}
            {available && inheritedActive && (
                <p className="text-xs text-neutral-500">
                    Using the institute&apos;s avatar ({inheritedAvatarId}).
                </p>
            )}
            {available && !active && (
                <>
                    {!photo && (
                        <p className="text-xs text-warning-700">
                            Upload the teacher&apos;s face photo first (a clear front-facing photo,
                            one face, at least 340 px on the shorter side).
                        </p>
                    )}
                    <div className="flex items-start gap-2">
                        <Checkbox
                            id="avatar-consent"
                            checked={consent}
                            onCheckedChange={(v) => setConsent(v === true)}
                        />
                        <Label
                            htmlFor="avatar-consent"
                            className="text-xs font-normal text-neutral-700"
                        >
                            The person in the photo has agreed that we may create and show an
                            animated likeness of them to learners of this institute.
                        </Label>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <MyButton
                            type="button"
                            buttonType="secondary"
                            scale="small"
                            layoutVariant="default"
                            disabled={!photo || !consent || !!jobId}
                            onClick={() => void create()}
                        >
                            {jobId ? <CircleNotch className="size-4 animate-spin" /> : null}
                            {jobId ? `Creating… (${status})` : 'Create avatar from the face photo'}
                        </MyButton>
                    </div>
                    <div className="flex flex-wrap items-end gap-2 pt-1">
                        <div className="flex min-w-56 flex-1 flex-col gap-1">
                            <Label htmlFor="avatar-manual-id" className="text-xs text-neutral-600">
                                Or use an avatar already created in Spatius Studio (avatar ID)
                            </Label>
                            <Input
                                id="avatar-manual-id"
                                value={manualId}
                                placeholder="avatar_…"
                                className="h-8 text-xs"
                                onChange={(e) => setManualId(e.target.value)}
                            />
                        </div>
                        <MyButton
                            type="button"
                            buttonType="secondary"
                            scale="small"
                            layoutVariant="default"
                            disabled={!manualId.trim() || !consent}
                            onClick={() => onChange('spatius', manualId.trim())}
                        >
                            Use this avatar
                        </MyButton>
                    </div>
                </>
            )}
            {available && active && (
                <button
                    type="button"
                    className="text-xs text-neutral-500 underline-offset-2 hover:underline"
                    onClick={() => onChange('none', '')}
                >
                    Remove the avatar
                </button>
            )}
        </div>
    );
};
