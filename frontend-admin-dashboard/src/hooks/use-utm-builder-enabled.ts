import { useQuery } from '@tanstack/react-query';
import {
    DEFAULT_UTM_SETTINGS,
    GTM_SETTINGS_QUERY_KEY,
    fetchGtmSettings,
    type UtmSettingsData,
} from '@/services/gtm-settings';

export interface UtmBuilderSwitch {
    /**
     * Whether to show the "Generate UTM link" action. Unlike the short-links
     * switch this is pessimistic — `false` until the setting is known — because
     * it ships OFF. An optimistic `true` would flash a menu item into every
     * share surface for the overwhelming majority of institutes that never turn
     * it on, and then yank it away a beat later.
     */
    enabled: boolean;
    /** Defaults and pick lists the builder pre-fills from. */
    settings: UtmSettingsData;
    /** The institute's actual preference is now known (success or failure). */
    isResolved: boolean;
}

/**
 * The institute's UTM link-builder switch (`GTM_SETTING.utm.enabled`).
 *
 * Shares the settings page's react-query key, so however many share surfaces
 * are on screen they all resolve from one cached request, and saving in
 * Settings → GTM invalidates that key so a flip shows up without a reload.
 */
export function useUtmBuilderEnabled(): UtmBuilderSwitch {
    const { data, isSuccess, isError } = useQuery({
        queryKey: GTM_SETTINGS_QUERY_KEY,
        queryFn: fetchGtmSettings,
        staleTime: 5 * 60 * 1000,
    });

    return {
        enabled: data?.utm?.enabled === true,
        settings: data?.utm ?? DEFAULT_UTM_SETTINGS,
        isResolved: isSuccess || isError,
    };
}
