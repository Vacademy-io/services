import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { MyButton } from '@/components/design-system/button';
import { toast } from 'sonner';
import {
    DEFAULT_GTM_SETTINGS,
    GTM_SETTINGS_QUERY_KEY,
    fetchGtmSettings,
    saveGtmSettings,
    type GtmSettingsData,
    type UtmSettingsData,
} from '@/services/gtm-settings';
import { SUGGESTED_MEDIUMS, SUGGESTED_SOURCES, normalizeUtmValue } from '@/lib/utm';

const GTM_ID_PATTERN = /^GTM-[A-Z0-9]+$/;

/** Comma/newline separated free text ⇄ the stored string[] pick list. */
const parseList = (raw: string): string[] =>
    Array.from(
        new Set(
            raw
                .split(/[,\n]/)
                .map((entry) => normalizeUtmValue(entry))
                .filter(Boolean)
        )
    );

export default function GtmSettings() {
    const { t } = useTranslation('settingsGtm');
    const queryClient = useQueryClient();
    const [settings, setSettings] = useState<GtmSettingsData>(DEFAULT_GTM_SETTINGS);
    const [hasChanges, setHasChanges] = useState(false);
    // Held as raw text so a half-typed "whatsapp, face" is not normalised out
    // from under the cursor on every keystroke.
    const [sourcesText, setSourcesText] = useState('');
    const [mediumsText, setMediumsText] = useState('');

    const { data, isLoading } = useQuery({
        queryKey: GTM_SETTINGS_QUERY_KEY,
        queryFn: fetchGtmSettings,
        staleTime: 5 * 60 * 1000,
    });

    useEffect(() => {
        if (data) {
            setSettings(data);
            setSourcesText(data.utm.sources.join(', '));
            setMediumsText(data.utm.mediums.join(', '));
            setHasChanges(false);
        }
    }, [data]);

    const { mutate: save, isPending: saving } = useMutation({
        mutationFn: saveGtmSettings,
        onSuccess: () => {
            toast.success(t('toasts.saveSuccess'));
            setHasChanges(false);
            // Every share surface's "Generate UTM link" gate reads through this
            // key, so flipping the switch here shows up without a reload.
            queryClient.invalidateQueries({ queryKey: GTM_SETTINGS_QUERY_KEY });
        },
        onError: () => {
            toast.error(t('toasts.saveError'));
        },
    });

    const update = (patch: Partial<GtmSettingsData>) => {
        setSettings((prev) => ({ ...prev, ...patch }));
        setHasChanges(true);
    };

    const updateUtm = (patch: Partial<UtmSettingsData>) => {
        setSettings((prev) => ({ ...prev, utm: { ...prev.utm, ...patch } }));
        setHasChanges(true);
    };

    const handleSave = () => {
        if (settings.enabled && !GTM_ID_PATTERN.test(settings.containerId)) {
            toast.error(t('errors.invalidContainerId'));
            return;
        }
        save({
            ...settings,
            utm: {
                ...settings.utm,
                sources: parseList(sourcesText),
                mediums: parseList(mediumsText),
            },
        });
    };

    if (isLoading) {
        return <div className="p-6 text-sm text-muted-foreground">{t('loading')}</div>;
    }

    const utm = settings.utm;

    return (
        <div className="space-y-6 p-6">
            <Card>
                <CardHeader>
                    <CardTitle>{t('card.title')}</CardTitle>
                    <CardDescription>
                        {t('card.description.part1')}
                        <code>enrollment_success</code>
                        {t('card.description.part2')}
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="flex items-center gap-3">
                        <Switch
                            id="gtm-enabled"
                            checked={settings.enabled}
                            onCheckedChange={(v) => update({ enabled: v })}
                        />
                        <Label htmlFor="gtm-enabled" className="cursor-pointer">
                            {settings.enabled ? t('toggle.enabled') : t('toggle.disabled')}
                        </Label>
                    </div>

                    <div className="space-y-1.5">
                        <Label htmlFor="gtm-container-id">{t('containerId.label')}</Label>
                        <Input
                            id="gtm-container-id"
                            placeholder={t('containerId.placeholder')}
                            value={settings.containerId}
                            disabled={!settings.enabled}
                            onChange={(e) =>
                                update({ containerId: e.target.value.toUpperCase().trim() })
                            }
                            className="max-w-xs"
                        />
                        <p className="text-xs text-muted-foreground">{t('containerId.hint')}</p>
                    </div>
                </CardContent>
            </Card>

            {/* ── UTM link builder ──────────────────────────────────────────
                Ships OFF. When off, the "Generate UTM link" action is hidden
                from every share surface; attribution that arrives on a link
                someone tagged by hand is still recorded, so switching this on
                later reveals history rather than starting from zero. */}
            <Card>
                <CardHeader>
                    <CardTitle>{t('utm.card.title')}</CardTitle>
                    <CardDescription>{t('utm.card.description')}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-5">
                    <div className="flex items-center gap-3">
                        <Switch
                            id="utm-enabled"
                            checked={utm.enabled}
                            onCheckedChange={(v) => updateUtm({ enabled: v })}
                        />
                        <Label htmlFor="utm-enabled" className="cursor-pointer">
                            {utm.enabled ? t('toggle.enabled') : t('toggle.disabled')}
                        </Label>
                    </div>

                    <p className="text-xs text-muted-foreground">{t('utm.surfacesHint')}</p>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <div className="space-y-1.5">
                            <Label htmlFor="utm-default-source">
                                {t('utm.defaultSource.label')}
                            </Label>
                            <Input
                                id="utm-default-source"
                                list="utm-settings-source-suggestions"
                                placeholder={t('utm.defaultSource.placeholder')}
                                value={utm.defaultSource}
                                disabled={!utm.enabled}
                                onChange={(e) =>
                                    updateUtm({ defaultSource: normalizeUtmValue(e.target.value) })
                                }
                                className="w-full"
                            />
                            <datalist id="utm-settings-source-suggestions">
                                {SUGGESTED_SOURCES.map((s) => (
                                    <option key={s} value={s} />
                                ))}
                            </datalist>
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="utm-default-medium">
                                {t('utm.defaultMedium.label')}
                            </Label>
                            <Input
                                id="utm-default-medium"
                                list="utm-settings-medium-suggestions"
                                placeholder={t('utm.defaultMedium.placeholder')}
                                value={utm.defaultMedium}
                                disabled={!utm.enabled}
                                onChange={(e) =>
                                    updateUtm({ defaultMedium: normalizeUtmValue(e.target.value) })
                                }
                                className="w-full"
                            />
                            <datalist id="utm-settings-medium-suggestions">
                                {SUGGESTED_MEDIUMS.map((m) => (
                                    <option key={m} value={m} />
                                ))}
                            </datalist>
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="utm-sources">{t('utm.sources.label')}</Label>
                            <Input
                                id="utm-sources"
                                placeholder={t('utm.sources.placeholder')}
                                value={sourcesText}
                                disabled={!utm.enabled}
                                onChange={(e) => {
                                    setSourcesText(e.target.value);
                                    setHasChanges(true);
                                }}
                                className="w-full"
                            />
                            <p className="text-xs text-muted-foreground">{t('utm.sources.hint')}</p>
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="utm-mediums">{t('utm.mediums.label')}</Label>
                            <Input
                                id="utm-mediums"
                                placeholder={t('utm.mediums.placeholder')}
                                value={mediumsText}
                                disabled={!utm.enabled}
                                onChange={(e) => {
                                    setMediumsText(e.target.value);
                                    setHasChanges(true);
                                }}
                                className="w-full"
                            />
                            <p className="text-xs text-muted-foreground">{t('utm.mediums.hint')}</p>
                        </div>
                    </div>

                    <div className="flex items-start gap-3">
                        <Switch
                            id="utm-require-campaign"
                            checked={utm.requireCampaign}
                            disabled={!utm.enabled}
                            onCheckedChange={(v) => updateUtm({ requireCampaign: v })}
                        />
                        <div>
                            <Label htmlFor="utm-require-campaign" className="cursor-pointer">
                                {t('utm.requireCampaign.label')}
                            </Label>
                            <p className="text-xs text-muted-foreground">
                                {t('utm.requireCampaign.hint')}
                            </p>
                        </div>
                    </div>
                </CardContent>
            </Card>

            <div className="flex justify-end">
                <MyButton
                    buttonType="primary"
                    scale="medium"
                    onClick={handleSave}
                    disable={saving || !hasChanges}
                >
                    {saving ? t('footer.saving') : t('footer.save')}
                </MyButton>
            </div>
        </div>
    );
}
