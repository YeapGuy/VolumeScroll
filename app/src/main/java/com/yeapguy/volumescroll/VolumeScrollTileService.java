package com.yeapguy.volumescroll;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class VolumeScrollTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();

        if (isLocked()) {
            unlockAndRun(this::toggleFeature);
        } else {
            toggleFeature();
        }
    }

    private void toggleFeature() {
        SettingsRepository settingsRepository = new SettingsRepository(this);
        boolean enabled = settingsRepository.isFeatureEnabled();
        settingsRepository.setFeatureEnabled(!enabled);
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        SettingsRepository settingsRepository = new SettingsRepository(this);
        boolean enabled = settingsRepository.isFeatureEnabled();

        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_tile));
        tile.setLabel(getString(R.string.quick_tile_label));
        tile.setSubtitle(getString(enabled
                ? R.string.quick_tile_state_on
                : R.string.quick_tile_state_off));
        tile.updateTile();
    }
}
