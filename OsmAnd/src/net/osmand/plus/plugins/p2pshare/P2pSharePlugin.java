/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.plugins.OsmandPlugin;
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter;
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem;

/**
 * LAMPP Phase 6: P2P Content Sharing Plugin
 *
 * Enables peer-to-peer sharing of maps, Wikipedia ZIMs, LLM models, and the app itself
 * between devices without internet connectivity.
 *
 * Technical approach:
 * - BLE beaconing for peer discovery (10-50m range, low power)
 * - WiFi Direct for high-speed transfer (250 Mbps)
 * - Bluetooth Classic fallback (720 Kbps)
 *
 * All communication is offline-first and works without Google Play Services.
 */
public class P2pSharePlugin extends OsmandPlugin {

    public static final String ID = "osmand.p2pshare";

    private P2pShareManager shareManager;

    public P2pSharePlugin(@NonNull OsmandApplication app) {
        super(app);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return app.getString(R.string.p2p_share_name);
    }

    @Override
    public CharSequence getDescription(boolean linksEnabled) {
        return app.getString(R.string.p2p_share_description);
    }

    @Override
    public int getLogoResourceId() {
        return R.drawable.ic_action_bluetooth;
    }

    @Override
    public boolean isEnableByDefault() {
        return true; // P2P sharing is a core Lampp feature
    }

    @Override
    public boolean init(@NonNull OsmandApplication app, @Nullable Activity activity) {
        shareManager = new P2pShareManager(app);
        return true;
    }

    @Override
    public void disable(@NonNull OsmandApplication app) {
        if (shareManager != null) {
            shareManager.shutdown();
            shareManager = null;
        }
    }

    @Override
    protected void registerOptionsMenuItems(@NonNull MapActivity mapActivity, @NonNull ContextMenuAdapter helper) {
        // P2P Share is now accessed via the LAMPP side tab bar — no drawer item needed
    }

    @Nullable
    public P2pShareManager getShareManager() {
        return shareManager;
    }
}
