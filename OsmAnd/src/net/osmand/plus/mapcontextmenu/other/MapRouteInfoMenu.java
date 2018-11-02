package net.osmand.plus.mapcontextmenu.other;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.ListPopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import net.osmand.AndroidUtils;
import net.osmand.Location;
import net.osmand.ValueHolder;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.GeocodingLookupService;
import net.osmand.plus.GeocodingLookupService.AddressLookupRequest;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.MapMarkersHelper;
import net.osmand.plus.MapMarkersHelper.MapMarker;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings.OsmandPreference;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.TargetPointsHelper.TargetPoint;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.actions.AppModeDialog;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.MapMarkerDialogHelper;
import net.osmand.plus.mapcontextmenu.InterceptorLinearLayout;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.plus.mapmarkers.MapMarkerSelectionFragment;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.routing.RoutingHelper.IRouteInformationListener;
import net.osmand.plus.views.MapControlsLayer;
import net.osmand.plus.views.OsmandMapTileView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MapRouteInfoMenu implements IRouteInformationListener {

	public static class MenuState {
		public static final int HEADER_ONLY = 1;
		public static final int HALF_SCREEN = 2;
		public static final int FULL_SCREEN = 4;
	}

	public static int directionInfo = -1;
	public static boolean controlVisible = false;
	private final MapContextMenu contextMenu;
	private final RoutingHelper routingHelper;
	private OsmandMapTileView mapView;
	private GeocodingLookupService geocodingLookupService;
	private boolean selectFromMapTouch;
	private boolean selectFromMapForTarget;
	private boolean selectFromMapForIntermediate;

	private boolean showMenu = false;
	private static boolean visible;
	private MapActivity mapActivity;
	private MapControlsLayer mapControlsLayer;
	public static final String TARGET_SELECT = "TARGET_SELECT";
	private boolean nightMode;
	private boolean switched;

	private AddressLookupRequest startPointRequest;
	private AddressLookupRequest targetPointRequest;
	private List<LatLon> intermediateRequestsLatLon = new ArrayList<>();
	private OnDismissListener onDismissListener;

	private OnMarkerSelectListener onMarkerSelectListener;
	private InterceptorLinearLayout mainView;

	private int currentMenuState;
	private boolean portraitMode;

	private static final long SPINNER_MY_LOCATION_ID = 1;
	public static final long SPINNER_FAV_ID = 2;
	public static final long SPINNER_MAP_ID = 3;
	public static final long SPINNER_ADDRESS_ID = 4;
	private static final long SPINNER_START_ID = 5;
	private static final long SPINNER_FINISH_ID = 6;
	private static final long SPINNER_HINT_ID = 100;
	public static final long SPINNER_MAP_MARKER_1_ID = 301;
	public static final long SPINNER_MAP_MARKER_2_ID = 302;
	private static final long SPINNER_MAP_MARKER_3_ID = 303;
	public static final long SPINNER_MAP_MARKER_MORE_ID = 350;

	public interface OnMarkerSelectListener {
		void onSelect(int index, boolean target, boolean intermediate);
	}

	public MapRouteInfoMenu(MapActivity mapActivity, MapControlsLayer mapControlsLayer) {
		this.mapActivity = mapActivity;
		this.mapControlsLayer = mapControlsLayer;
		contextMenu = mapActivity.getContextMenu();
		routingHelper = mapActivity.getRoutingHelper();
		mapView = mapActivity.getMapView();
		routingHelper.addListener(this);
		portraitMode = AndroidUiHelper.isOrientationPortrait(mapActivity);
		currentMenuState = getInitialMenuState();

		geocodingLookupService = mapActivity.getMyApplication().getGeocodingLookupService();
		onMarkerSelectListener = new OnMarkerSelectListener() {
			@Override
			public void onSelect(int index, boolean target, boolean intermediate) {
				selectMapMarker(index, target, intermediate);
			}
		};
	}

	private int getInitialMenuState() {
		if (!portraitMode) {
			return MenuState.FULL_SCREEN;
		} else {
			return getInitialMenuStatePortrait();
		}
	}

	public OnDismissListener getOnDismissListener() {
		return onDismissListener;
	}

	public void setOnDismissListener(OnDismissListener onDismissListener) {
		this.onDismissListener = onDismissListener;
	}

	public boolean isSelectFromMapTouch() {
		return selectFromMapTouch;
	}

	public void cancelSelectionFromMap() {
		selectFromMapTouch = false;
	}

	public boolean onSingleTap(PointF point, RotatedTileBox tileBox) {
		if (selectFromMapTouch) {
			LatLon latlon = tileBox.getLatLonFromPixel(point.x, point.y);
			selectFromMapTouch = false;
			if (selectFromMapForIntermediate) {
				getTargets().navigateToPoint(latlon, true, getTargets().getIntermediatePoints().size());
			} else if (selectFromMapForTarget) {
				getTargets().navigateToPoint(latlon, true, -1);
			} else {
				getTargets().setStartPoint(latlon, true, null);
			}
			show();
			if (selectFromMapForIntermediate && getTargets().checkPointToNavigateShort()) {
				mapActivity.getMapActions().openIntermediatePointsDialog();
			}
			return true;
		}
		return false;
	}

	public OnMarkerSelectListener getOnMarkerSelectListener() {
		return onMarkerSelectListener;
	}

	private void cancelStartPointAddressRequest() {
		if (startPointRequest != null) {
			geocodingLookupService.cancel(startPointRequest);
			startPointRequest = null;
		}
	}

	private void cancelTargetPointAddressRequest() {
		if (targetPointRequest != null) {
			geocodingLookupService.cancel(targetPointRequest);
			targetPointRequest = null;
		}
	}

	public void setVisible(boolean visible) {
		if (visible) {
			if (showMenu) {
				show();
				showMenu = false;
			}
			controlVisible = true;
		} else {
			hide();
			controlVisible = false;
		}
	}


	public int getCurrentMenuState() {
		return currentMenuState;
	}

	public int getSupportedMenuStates() {
		if (!portraitMode) {
			return MenuState.FULL_SCREEN;
		} else {
			return getSupportedMenuStatesPortrait();
		}
	}

	protected int getInitialMenuStatePortrait() {
		return MenuState.HEADER_ONLY;
	}

	protected int getSupportedMenuStatesPortrait() {
		return MenuState.HEADER_ONLY | MenuState.HALF_SCREEN | MenuState.FULL_SCREEN;
	}

	public boolean slideUp() {
		int v = currentMenuState;
		for (int i = 0; i < 2; i++) {
			v = v << 1;
			if ((v & getSupportedMenuStates()) != 0) {
				currentMenuState = v;
				return true;
			}
		}
		return false;
	}

	public boolean slideDown() {
		int v = currentMenuState;
		for (int i = 0; i < 2; i++) {
			v = v >> 1;
			if ((v & getSupportedMenuStates()) != 0) {
				currentMenuState = v;
				return true;
			}
		}
		return false;
	}

	public void showHideMenu() {
		intermediateRequestsLatLon.clear();
		if (isVisible()) {
			hide();
		} else {
			show();
		}
	}

	public void updateRouteCalculationProgress(int progress) {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null) {
			fragmentRef.get().updateRouteCalculationProgress(progress);
		}
	}

	public void routeCalculationFinished() {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null) {
			fragmentRef.get().hideRouteCalculationProgressBar();
		}
	}

	public void updateMenu() {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null)
			fragmentRef.get().updateInfo();
	}

	public void updateFromIcon() {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null)
			fragmentRef.get().updateFromIcon();
	}

	public void updateInfo(final InterceptorLinearLayout main) {
		mainView = main;
		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		updateViaView(main);
		updateFromSpinner(main);
		updateToSpinner(main);
		updateApplicationModes(main);
		updateApplicationModesOptions(main);

		mapControlsLayer.updateRouteButtons(main, true);
	}

	private void updateApplicationModesOptions(final View parentView) {
		parentView.findViewById(R.id.app_modes_options).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				availableProfileDialog();
			}
		});
	}

	private void availableProfileDialog() {
		AlertDialog.Builder b = new AlertDialog.Builder(mapActivity);
		final OsmandSettings settings = mapActivity.getMyApplication().getSettings();
		final List<ApplicationMode> modes = ApplicationMode.allPossibleValues();
		modes.remove(ApplicationMode.DEFAULT);
		final Set<ApplicationMode> selected = new LinkedHashSet<ApplicationMode>(ApplicationMode.values(settings));
		selected.remove(ApplicationMode.DEFAULT);
		View v = AppModeDialog.prepareAppModeView(mapActivity, modes, selected, null, false, true, false,
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						StringBuilder vls = new StringBuilder(ApplicationMode.DEFAULT.getStringKey() + ",");
						for (ApplicationMode mode : modes) {
							if (selected.contains(mode)) {
								vls.append(mode.getStringKey()).append(",");
							}
						}
						settings.AVAILABLE_APP_MODES.set(vls.toString());
					}
				});
		b.setTitle(R.string.profile_settings);
		b.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				updateApplicationModes(mainView);
			}
		});
		b.setView(v);
		b.show();
	}


	private void updateApplicationModes(final View parentView) {
		//final OsmandSettings settings = mapActivity.getMyApplication().getSettings();
		//ApplicationMode am = settings.APPLICATION_MODE.get();
		final ApplicationMode am = routingHelper.getAppMode();
		final Set<ApplicationMode> selected = new HashSet<>();
		selected.add(am);
		ViewGroup vg = (ViewGroup) parentView.findViewById(R.id.app_modes);
		vg.removeAllViews();
		View.OnClickListener listener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (selected.size() > 0) {
					ApplicationMode next = selected.iterator().next();
					OsmandPreference<ApplicationMode> appMode
							= mapActivity.getMyApplication().getSettings().APPLICATION_MODE;
					if (routingHelper.isFollowingMode() && appMode.get() == am) {
						appMode.set(next);
						//updateMenu();
					}
					routingHelper.setAppMode(next);
					mapActivity.getMyApplication().initVoiceCommandPlayer(mapActivity, next, true, null, false, false);
					routingHelper.recalculateRouteDueToSettingsChange();
				}
			}
		};
		OsmandSettings settings = mapActivity.getMyApplication().getSettings();
		final List<ApplicationMode> values = new ArrayList<ApplicationMode>(ApplicationMode.values(settings));
		values.remove(ApplicationMode.DEFAULT);

		View ll = mapActivity.getLayoutInflater().inflate(R.layout.mode_toggles, vg);
		ll.setBackgroundColor(ContextCompat.getColor(mapActivity, nightMode ? R.color.route_info_bg_dark : R.color.route_info_bg_light));
		final View[] buttons = new View[values.size()];
		int k = 0;
		for (ApplicationMode ma : values) {
			buttons[k++] = createToggle(mapActivity.getLayoutInflater(), (OsmandApplication) mapActivity.getApplication(), (LinearLayout) ll.findViewById(R.id.app_modes_content), ma, true);
		}
		for (int i = 0; i < buttons.length; i++) {
			updateButtonState((OsmandApplication) mapActivity.getApplication(), values, selected, listener, buttons, i, true, true);
		}
	}

	private void updateButtonState(final OsmandApplication ctx, final List<ApplicationMode> visible,
	                               final Set<ApplicationMode> selected, final View.OnClickListener onClickListener, final View[] buttons,
	                               int i, final boolean singleChoice, final boolean useMapTheme) {
		if (buttons[i] != null) {
			View tb = buttons[i];
			final ApplicationMode mode = visible.get(i);
			final boolean checked = selected.contains(mode);
			ImageView iv = (ImageView) tb.findViewById(R.id.app_mode_icon);
			if (checked) {
				iv.setImageDrawable(ctx.getUIUtilities().getIcon(mode.getSmallIconDark(), nightMode ? R.color.route_info_checked_mode_icon_color_dark : R.color.route_info_active_light));
				iv.setContentDescription(String.format("%s %s", mode.toHumanString(ctx), ctx.getString(R.string.item_checked)));
				iv.setBackgroundResource(R.drawable.btn_border_transparent);
				tb.setBackgroundDrawable(null);
			} else {
				if (useMapTheme) {
					iv.setImageDrawable(ctx.getUIUtilities().getIcon(mode.getSmallIconDark(), R.color.route_info_unchecked_mode_icon_color));
					iv.setBackgroundDrawable(null);
					tb.setBackgroundResource(AndroidUtils.resolveAttribute(ctx, android.R.attr.selectableItemBackground));
				} else {
					iv.setImageDrawable(ctx.getUIUtilities().getThemedIcon(mode.getSmallIconDark()));
				}
				iv.setContentDescription(String.format("%s %s", mode.toHumanString(ctx), ctx.getString(R.string.item_unchecked)));
			}
			tb.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					boolean isChecked = !checked;
					if (singleChoice) {
						if (isChecked) {
							selected.clear();
							selected.add(mode);
						}
					} else {
						if (isChecked) {
							selected.add(mode);
						} else {
							selected.remove(mode);
						}
					}
					if (onClickListener != null) {
						onClickListener.onClick(null);
					}
					for (int i = 0; i < visible.size(); i++) {
						updateButtonState(ctx, visible, selected, onClickListener, buttons, i, singleChoice, useMapTheme);
					}
				}
			});
		}
	}


	private View createToggle(LayoutInflater layoutInflater, OsmandApplication ctx, LinearLayout layout, ApplicationMode mode, boolean useMapTheme) {
		int metricsX = (int) ctx.getResources().getDimension(R.dimen.route_info_modes_height);
		int metricsY = (int) ctx.getResources().getDimension(R.dimen.route_info_modes_height);
		View tb = layoutInflater.inflate(R.layout.mode_view_route_preparation, null);
		ImageView iv = (ImageView) tb.findViewById(R.id.app_mode_icon);
		iv.setImageDrawable(ctx.getUIUtilities().getIcon(mode.getSmallIconDark(), nightMode ? R.color.route_info_checked_mode_icon_color_dark : R.color.route_info_active_light));
		iv.setContentDescription(mode.toHumanString(ctx));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(metricsX, metricsY);
		layout.addView(tb, lp);
		return tb;
	}

	private void updateViaView(final InterceptorLinearLayout parentView) {
		String via = generateViaDescription();
		View viaLayout = parentView.findViewById(R.id.ViaLayout);
		View viaLayoutDivider = parentView.findViewById(R.id.viaLayoutDivider);
		LinearLayout swapDirectionButton = (LinearLayout) parentView.findViewById(R.id.from_button);
		ImageView swapDirectionView = (ImageView) parentView.findViewById(R.id.from_button_image_view);
		if (via.length() == 0) {
			viaLayout.setVisibility(View.GONE);
			viaLayoutDivider.setVisibility(View.GONE);
			swapDirectionButton.setVisibility(View.VISIBLE);
		} else {
			swapDirectionButton.setVisibility(View.GONE);
			viaLayout.setVisibility(View.VISIBLE);
			viaLayoutDivider.setVisibility(View.VISIBLE);
			((TextView) parentView.findViewById(R.id.ViaView)).setText(via);
		}

		viaLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (getTargets().checkPointToNavigateShort()) {
					mapActivity.getMapActions().openIntermediatePointsDialog();
				}
			}
		});

		ImageView viaIcon = (ImageView) parentView.findViewById(R.id.viaIcon);
		viaIcon.setImageDrawable(getIconOrig(R.drawable.list_intermediate));

		LinearLayout viaButton = (LinearLayout) parentView.findViewById(R.id.via_button);
		ImageView viaButtonImageView = (ImageView) parentView.findViewById(R.id.via_button_image_view);

		viaButtonImageView.setImageDrawable(mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_edit_dark,
				isLight() ? R.color.route_info_control_icon_color_light : R.color.route_info_control_icon_color_dark));
		viaButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (getTargets().checkPointToNavigateShort()) {
					mapActivity.getMapActions().openIntermediatePointsDialog();
				}
			}
		});
	}

	private void updateToSpinner(final InterceptorLinearLayout parentView) {
		final Spinner toSpinner = setupToSpinner(parentView);
		toSpinner.setClickable(false);
		final View toLayout = parentView.findViewById(R.id.ToLayout);
		toSpinner.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				event.offsetLocation(AndroidUtils.dpToPx(mapActivity, 48f), 0);
				toLayout.onTouchEvent(event);
				return true;
			}
		});
		toSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, final long id) {
				parentView.post(new Runnable() {
					@Override
					public void run() {
						if (id == SPINNER_FAV_ID) {
							selectFavorite(parentView, true, false);
						} else if (id == SPINNER_MAP_ID) {
							selectOnScreen(true, false);
						} else if (id == SPINNER_ADDRESS_ID) {
							mapActivity.showQuickSearch(MapActivity.ShowQuickSearchMode.DESTINATION_SELECTION, false);
							setupToSpinner(parentView);
						} else if (id == SPINNER_MAP_MARKER_MORE_ID) {
							selectMapMarker(-1, true, false);
							setupToSpinner(parentView);
						} else if (id == SPINNER_MAP_MARKER_1_ID) {
							selectMapMarker(0, true, false);
						} else if (id == SPINNER_MAP_MARKER_2_ID) {
							selectMapMarker(1, true, false);
						} else if (id == SPINNER_MAP_MARKER_3_ID) {
							selectMapMarker(2, true, false);
						}
					}
				});
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		toLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				toSpinner.performClick();
			}
		});

		final LinearLayout toButton = (LinearLayout) parentView.findViewById(R.id.to_button);
		ImageView toButtonImageView = (ImageView) parentView.findViewById(R.id.to_button_image_view);

		toButtonImageView.setImageDrawable(mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_plus,
				isLight() ? R.color.route_info_control_icon_color_light : R.color.route_info_control_icon_color_dark));
		toButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mapActivity != null) {
					final ListPopupWindow popup = new ListPopupWindow(mapActivity);
					popup.setAnchorView(toLayout);
					popup.setDropDownGravity(Gravity.END | Gravity.TOP);
					popup.setModal(true);
					popup.setAdapter(getIntermediatesPopupAdapter(mapActivity));
					popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
						@Override
						public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
							boolean hideDashboard = false;
							if (id == MapRouteInfoMenu.SPINNER_FAV_ID) {
								selectFavorite(null, false, true);
							} else if (id == MapRouteInfoMenu.SPINNER_MAP_ID) {
								hideDashboard = true;
								selectOnScreen(false, true);
							} else if (id == MapRouteInfoMenu.SPINNER_ADDRESS_ID) {
								mapActivity.showQuickSearch(MapActivity.ShowQuickSearchMode.INTERMEDIATE_SELECTION, false);
							} else if (id == MapRouteInfoMenu.SPINNER_MAP_MARKER_MORE_ID) {
								selectMapMarker(-1, false, true);
							} else if (id == MapRouteInfoMenu.SPINNER_MAP_MARKER_1_ID) {
								selectMapMarker(0, false, true);
							} else if (id == MapRouteInfoMenu.SPINNER_MAP_MARKER_2_ID) {
								selectMapMarker(1, false, true);
							}
							popup.dismiss();
							if (hideDashboard) {
								mapActivity.getDashboard().hideDashboard();
							}
						}
					});
					popup.show();
				}
			}
		});

		updateToIcon(parentView);
	}

	private void updateToIcon(View parentView) {
		ImageView toIcon = (ImageView) parentView.findViewById(R.id.toIcon);
		toIcon.setImageDrawable(getIconOrig(R.drawable.list_destination));
	}

	private void updateFromSpinner(final InterceptorLinearLayout parentView) {
		final TargetPointsHelper targets = getTargets();
		final Spinner fromSpinner = setupFromSpinner(parentView);
		fromSpinner.setClickable(false);
		final View fromLayout = parentView.findViewById(R.id.FromLayout);
		fromSpinner.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				event.offsetLocation(AndroidUtils.dpToPx(mapActivity, 48f), 0);
				fromLayout.onTouchEvent(event);
				return true;
			}
		});
		fromSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, final long id) {
				parentView.post(new Runnable() {
					@Override
					public void run() {
						if (id == SPINNER_MY_LOCATION_ID) {
							if (targets.getPointToStart() != null) {
								targets.clearStartPoint(true);
								mapActivity.getMyApplication().getSettings().backupPointToStart();
							}
							updateFromIcon(parentView);
						} else if (id == SPINNER_FAV_ID) {
							selectFavorite(parentView, false, false);
						} else if (id == SPINNER_MAP_ID) {
							selectOnScreen(false, false);
						} else if (id == SPINNER_ADDRESS_ID) {
							mapActivity.showQuickSearch(MapActivity.ShowQuickSearchMode.START_POINT_SELECTION, false);
							setupFromSpinner(parentView);
						} else if (id == SPINNER_MAP_MARKER_MORE_ID) {
							selectMapMarker(-1, false, false);
							setupFromSpinner(parentView);
						} else if (id == SPINNER_MAP_MARKER_1_ID) {
							selectMapMarker(0, false, false);
						} else if (id == SPINNER_MAP_MARKER_2_ID) {
							selectMapMarker(1, false, false);
						} else if (id == SPINNER_MAP_MARKER_3_ID) {
							selectMapMarker(2, false, false);
						}
					}
				});
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		fromLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				fromSpinner.performClick();
			}
		});

		LinearLayout swapDirectionButton = (LinearLayout) parentView.findViewById(R.id.from_button);
		ImageView swapDirectionView = (ImageView) parentView.findViewById(R.id.from_button_image_view);
		if (generateViaDescription().length() == 0) {
			swapDirectionButton.setVisibility(View.VISIBLE);
		} else {
			swapDirectionButton.setVisibility(View.GONE);
		}
		swapDirectionView.setImageDrawable(mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_change_navigation_points,
				isLight() ? R.color.route_info_control_icon_color_light : R.color.route_info_control_icon_color_dark));
		swapDirectionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				TargetPointsHelper targetPointsHelper = getTargets();
				TargetPoint startPoint = targetPointsHelper.getPointToStart();
				TargetPoint endPoint = targetPointsHelper.getPointToNavigate();

				if (startPoint == null) {
					Location loc = mapActivity.getMyApplication().getLocationProvider().getLastKnownLocation();
					if (loc != null) {
						startPoint = TargetPoint.createStartPoint(new LatLon(loc.getLatitude(), loc.getLongitude()),
								new PointDescription(PointDescription.POINT_TYPE_MY_LOCATION,
										mapActivity.getString(R.string.shared_string_my_location)));
					}
				}

				if (startPoint != null && endPoint != null) {
					targetPointsHelper.navigateToPoint(startPoint.point, false, -1, startPoint.getPointDescription(mapActivity));
					targetPointsHelper.setStartPoint(endPoint.point, false, endPoint.getPointDescription(mapActivity));
					targetPointsHelper.updateRouteAndRefresh(true);

					updateInfo(mainView);
				}
			}
		});

		updateFromIcon(parentView);
	}

	public void updateFromIcon(View parentView) {
		((ImageView) parentView.findViewById(R.id.fromIcon)).setImageDrawable(ContextCompat.getDrawable(mapActivity,
				getTargets().getPointToStart() == null ? R.drawable.ic_action_location_color : R.drawable.list_startpoint));
	}

	public void selectOnScreen(boolean target, boolean intermediate) {
		selectFromMapTouch = true;
		selectFromMapForTarget = target;
		selectFromMapForIntermediate = intermediate;
		hide();
	}

	public void selectAddress(String name, LatLon l, final boolean target, final boolean intermediate) {
		PointDescription pd = new PointDescription(PointDescription.POINT_TYPE_ADDRESS, name);
		if (intermediate) {
			getTargets().navigateToPoint(l, true, getTargets().getIntermediatePoints().size(), pd);
		} else if (target) {
			getTargets().navigateToPoint(l, true, -1, pd);
		} else {
			getTargets().setStartPoint(l, true, pd);
		}
		updateMenu();
	}

	public void selectFavorite(@Nullable final View parentView, final boolean target, final boolean intermediate) {
		FragmentManager fragmentManager = mapActivity.getSupportFragmentManager();
		FavouritesBottomSheetMenuFragment fragment = new FavouritesBottomSheetMenuFragment();
		Bundle args = new Bundle();
		args.putBoolean(FavouritesBottomSheetMenuFragment.TARGET, target);
		args.putBoolean(FavouritesBottomSheetMenuFragment.INTERMEDIATE, intermediate);
		fragment.setArguments(args);
		fragment.show(fragmentManager, FavouritesBottomSheetMenuFragment.TAG);
	}

	public void setupSpinners(final boolean target, final boolean intermediate) {
		if (!intermediate && mainView != null) {
			if (target) {
				setupToSpinner(mainView);
			} else {
				setupFromSpinner(mainView);
			}
		}
	}

	public void selectMapMarker(final int index, final boolean target, final boolean intermediate) {
		if (index != -1) {
			MapMarker m = mapActivity.getMyApplication().getMapMarkersHelper().getMapMarkers().get(index);
			LatLon point = new LatLon(m.getLatitude(), m.getLongitude());
			if (intermediate) {
				getTargets().navigateToPoint(point, true, getTargets().getIntermediatePoints().size(), m.getPointDescription(mapActivity));
			} else if (target) {
				getTargets().navigateToPoint(point, true, -1, m.getPointDescription(mapActivity));
			} else {
				getTargets().setStartPoint(point, true, m.getPointDescription(mapActivity));
			}
			updateFromIcon();

		} else {

			MapMarkerSelectionFragment selectionFragment = MapMarkerSelectionFragment.newInstance(target, intermediate);
			selectionFragment.show(mapActivity.getSupportFragmentManager(), MapMarkerSelectionFragment.TAG);
		}
	}

	private boolean isLight() {
		return !nightMode;
	}

	private Drawable getIconOrig(int iconId) {
		UiUtilities iconsCache = mapActivity.getMyApplication().getUIUtilities();
		return iconsCache.getIcon(iconId, 0);
	}

	public static int getDirectionInfo() {
		return directionInfo;
	}

	public static boolean isVisible() {
		return visible;
	}

	public WeakReference<MapRouteInfoMenuFragment> findMenuFragment() {
		Fragment fragment = mapActivity.getSupportFragmentManager().findFragmentByTag(MapRouteInfoMenuFragment.TAG);
		if (fragment != null && !fragment.isDetached()) {
			return new WeakReference<>((MapRouteInfoMenuFragment) fragment);
		} else {
			return null;
		}
	}

	public static boolean isControlVisible() {
		return controlVisible;
	}

	public static void showLocationOnMap(MapActivity mapActivity, double latitude, double longitude) {
		RotatedTileBox tb = mapActivity.getMapView().getCurrentRotatedTileBox().copy();
		int tileBoxWidthPx = 0;
		int tileBoxHeightPx = 0;

		MapRouteInfoMenu routeInfoMenu = mapActivity.getMapLayers().getMapControlsLayer().getMapRouteInfoMenu();
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = routeInfoMenu.findMenuFragment();
		if (fragmentRef != null) {
			MapRouteInfoMenuFragment f = fragmentRef.get();
			if (mapActivity.isLandscapeLayout()) {
				tileBoxWidthPx = tb.getPixWidth() - f.getWidth();
			} else {
				tileBoxHeightPx = tb.getPixHeight() - f.getHeight();
			}
		}
		mapActivity.getMapView().fitLocationToMap(latitude, longitude, mapActivity.getMapView().getZoom(),
				tileBoxWidthPx, tileBoxHeightPx, AndroidUtils.dpToPx(mapActivity, 40f), true);
	}

	@Override
	public void newRouteIsCalculated(boolean newRoute, ValueHolder<Boolean> showToast) {
		directionInfo = -1;
		updateMenu();
		if (isVisible()) {
			showToast.value = false;
		}
	}

	public String generateViaDescription() {
		TargetPointsHelper targets = getTargets();
		List<TargetPoint> points = targets.getIntermediatePointsNavigation();
		if (points.size() == 0) {
			return "";
		}
		StringBuilder via = new StringBuilder();
		for (int i = 0; i < points.size(); i++) {
			if (i > 0) {
				via.append(" ");
			}
			TargetPoint p = points.get(i);
			String description = p.getOnlyName();
			via.append(getRoutePointDescription(p.point, description));
			boolean needAddress = new PointDescription(PointDescription.POINT_TYPE_LOCATION, description).isSearchingAddress(mapActivity)
					&& !intermediateRequestsLatLon.contains(p.point);
			if (needAddress) {
				AddressLookupRequest lookupRequest = new AddressLookupRequest(p.point, new GeocodingLookupService.OnAddressLookupResult() {
					@Override
					public void geocodingDone(String address) {
						updateMenu();
					}
				}, null);
				intermediateRequestsLatLon.add(p.point);
				geocodingLookupService.lookupAddress(lookupRequest);
			}
		}
		return via.toString();
	}

	public String getRoutePointDescription(double lat, double lon) {
		return mapActivity.getString(R.string.route_descr_lat_lon, lat, lon);
	}

	public String getRoutePointDescription(LatLon l, String d) {
		if (d != null && d.length() > 0) {
			return d.replace(':', ' ');
		}
		if (l != null) {
			return mapActivity.getString(R.string.route_descr_lat_lon, l.getLatitude(), l.getLongitude());
		}
		return "";
	}

	private Spinner setupFromSpinner(View view) {
		List<RouteSpinnerRow> fromActions = new ArrayList<>();
		fromActions.add(new RouteSpinnerRow(SPINNER_MY_LOCATION_ID, R.drawable.ic_action_get_my_location,
				mapActivity.getString(R.string.shared_string_my_location)));
		fromActions.add(new RouteSpinnerRow(SPINNER_FAV_ID, R.drawable.ic_action_fav_dark,
				mapActivity.getString(R.string.shared_string_favorite) + mapActivity.getString(R.string.shared_string_ellipsis)));
		fromActions.add(new RouteSpinnerRow(SPINNER_MAP_ID, R.drawable.ic_action_marker_dark,
				mapActivity.getString(R.string.shared_string_select_on_map)));
		fromActions.add(new RouteSpinnerRow(SPINNER_ADDRESS_ID, R.drawable.ic_action_home_dark,
				mapActivity.getString(R.string.shared_string_address) + mapActivity.getString(R.string.shared_string_ellipsis)));

		TargetPoint start = getTargets().getPointToStart();
		int startPos = -1;
		if (start != null) {
			String oname = start.getOnlyName().length() > 0 ? start.getOnlyName()
					: (mapActivity.getString(R.string.route_descr_map_location) + " " + getRoutePointDescription(start.getLatitude(), start.getLongitude()));
			startPos = fromActions.size();
			fromActions.add(new RouteSpinnerRow(SPINNER_START_ID, R.drawable.ic_action_get_my_location, oname));

			final LatLon latLon = start.point;
			final PointDescription pointDescription = start.getOriginalPointDescription();
			boolean needAddress = pointDescription != null && pointDescription.isSearchingAddress(mapActivity);
			cancelStartPointAddressRequest();
			if (needAddress) {
				startPointRequest = new AddressLookupRequest(latLon, new GeocodingLookupService.OnAddressLookupResult() {
					@Override
					public void geocodingDone(String address) {
						startPointRequest = null;
						updateMenu();
					}
				}, null);
				geocodingLookupService.lookupAddress(startPointRequest);
			}
		}

		addMarkersToSpinner(fromActions);

		final Spinner fromSpinner = ((Spinner) view.findViewById(R.id.FromSpinner));
		RouteSpinnerArrayAdapter fromAdapter = new RouteSpinnerArrayAdapter(view.getContext());
		for (RouteSpinnerRow row : fromActions) {
			fromAdapter.add(row);
		}
		fromSpinner.setAdapter(fromAdapter);
		if (start != null) {
			fromSpinner.setSelection(startPos);
		} else {
			if (mapActivity.getMyApplication().getLocationProvider().getLastKnownLocation() == null) {
				fromSpinner.setPromptId(R.string.search_poi_location);
			}
			//fromSpinner.setSelection(0);
		}
		return fromSpinner;
	}

	private Spinner setupToSpinner(View view) {
		final Spinner toSpinner = ((Spinner) view.findViewById(R.id.ToSpinner));
		final TargetPointsHelper targets = getTargets();
		List<RouteSpinnerRow> toActions = new ArrayList<>();

		TargetPoint finish = getTargets().getPointToNavigate();
		if (finish != null) {
			toActions.add(new RouteSpinnerRow(SPINNER_FINISH_ID, R.drawable.ic_action_get_my_location,
					getRoutePointDescription(targets.getPointToNavigate().point,
							targets.getPointToNavigate().getOnlyName())));

			final LatLon latLon = finish.point;
			final PointDescription pointDescription = finish.getOriginalPointDescription();
			boolean needAddress = pointDescription != null && pointDescription.isSearchingAddress(mapActivity);
			cancelTargetPointAddressRequest();
			if (needAddress) {
				targetPointRequest = new AddressLookupRequest(latLon, new GeocodingLookupService.OnAddressLookupResult() {
					@Override
					public void geocodingDone(String address) {
						targetPointRequest = null;
						updateMenu();
					}
				}, null);
				geocodingLookupService.lookupAddress(targetPointRequest);
			}

		} else {
			toSpinner.setPromptId(R.string.route_descr_select_destination);
			toActions.add(new RouteSpinnerRow(SPINNER_HINT_ID, R.drawable.ic_action_get_my_location,
					mapActivity.getString(R.string.route_descr_select_destination)));
		}
		toActions.add(new RouteSpinnerRow(SPINNER_FAV_ID, R.drawable.ic_action_fav_dark,
				mapActivity.getString(R.string.shared_string_favorite) + mapActivity.getString(R.string.shared_string_ellipsis)));
		toActions.add(new RouteSpinnerRow(SPINNER_MAP_ID, R.drawable.ic_action_marker_dark,
				mapActivity.getString(R.string.shared_string_select_on_map)));
		toActions.add(new RouteSpinnerRow(SPINNER_ADDRESS_ID, R.drawable.ic_action_home_dark,
				mapActivity.getString(R.string.shared_string_address) + mapActivity.getString(R.string.shared_string_ellipsis)));

		addMarkersToSpinner(toActions);

		RouteSpinnerArrayAdapter toAdapter = new RouteSpinnerArrayAdapter(view.getContext());
		for (RouteSpinnerRow row : toActions) {
			toAdapter.add(row);
		}
		toSpinner.setAdapter(toAdapter);
		return toSpinner;
	}

	public RoutePopupListArrayAdapter getIntermediatesPopupAdapter(Context ctx) {
		List<RouteSpinnerRow> viaActions = new ArrayList<>();

		viaActions.add(new RouteSpinnerRow(SPINNER_FAV_ID, R.drawable.ic_action_fav_dark,
				mapActivity.getString(R.string.shared_string_favorite) + mapActivity.getString(R.string.shared_string_ellipsis)));
		viaActions.add(new RouteSpinnerRow(SPINNER_MAP_ID, R.drawable.ic_action_marker_dark,
				mapActivity.getString(R.string.shared_string_select_on_map)));
		viaActions.add(new RouteSpinnerRow(SPINNER_ADDRESS_ID, R.drawable.ic_action_home_dark,
				mapActivity.getString(R.string.shared_string_address) + mapActivity.getString(R.string.shared_string_ellipsis)));

		addMarkersToSpinner(viaActions);

		RoutePopupListArrayAdapter viaAdapter = new RoutePopupListArrayAdapter(ctx);
		for (RouteSpinnerRow row : viaActions) {
			viaAdapter.add(row);
		}

		return viaAdapter;
	}

	private void addMarkersToSpinner(List<RouteSpinnerRow> actions) {
		MapMarkersHelper markersHelper = mapActivity.getMyApplication().getMapMarkersHelper();
		List<MapMarker> markers = markersHelper.getMapMarkers();
		if (markers.size() > 0) {
			MapMarker m = markers.get(0);
			actions.add(new RouteSpinnerRow(SPINNER_MAP_MARKER_1_ID,
					MapMarkerDialogHelper.getMapMarkerIcon(mapActivity.getMyApplication(), m.colorIndex),
					m.getName(mapActivity)));
		}
		if (markers.size() > 1) {
			MapMarker m = markers.get(1);
			actions.add(new RouteSpinnerRow(SPINNER_MAP_MARKER_2_ID,
					MapMarkerDialogHelper.getMapMarkerIcon(mapActivity.getMyApplication(), m.colorIndex),
					m.getName(mapActivity)));
		}
		/*
		if (markers.size() > 2) {
			MapMarker m = markers.get(2);
			actions.add(new RouteSpinnerRow(SPINNER_MAP_MARKER_3_ID,
					MapMarkerDialogHelper.getMapMarkerIcon(mapActivity.getMyApplication(), m.colorIndex),
					m.getOnlyName()));
		}
		*/
		if (markers.size() > 2) {
			actions.add(new RouteSpinnerRow(SPINNER_MAP_MARKER_MORE_ID, 0,
					mapActivity.getString(R.string.map_markers_other)));
		}
	}

	private TargetPointsHelper getTargets() {
		return mapActivity.getMyApplication().getTargetPointsHelper();
	}

	@Override
	public void routeWasCancelled() {
		directionInfo = -1;
		// do not hide fragment (needed for use case entering Planning mode without destination)
	}

	@Override
	public void routeWasFinished() {
	}

	public void onDismiss() {
		visible = false;
		mapActivity.getMapView().setMapPositionX(0);
		mapActivity.getMapView().refreshMap();
		AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_route_land_left_margin), false);
		AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_right_widgets_panel), true);
		if (switched) {
			mapControlsLayer.switchToRouteFollowingLayout();
		}
		if (getTargets().getPointToNavigate() == null && !selectFromMapTouch) {
			mapActivity.getMapActions().stopNavigationWithoutConfirm();
		}
		if (onDismissListener != null) {
			onDismissListener.onDismiss(null);
		}
	}

	public void show() {
		if (!visible) {
			currentMenuState = getInitialMenuState();
			visible = true;
			switched = mapControlsLayer.switchToRoutePlanningLayout();
			boolean refreshMap = !switched;
			boolean portrait = AndroidUiHelper.isOrientationPortrait(mapActivity);
			if (!portrait) {
				mapActivity.getMapView().setMapPositionX(1);
				refreshMap = true;
			}

			if (refreshMap) {
				mapActivity.refreshMap();
			}

			MapRouteInfoMenuFragment.showInstance(mapActivity);

			if (!AndroidUiHelper.isXLargeDevice(mapActivity)) {
				AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_right_widgets_panel), false);
			}
			if (!portrait) {
				AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_route_land_left_margin), true);
			}
		}
	}

	public void hide() {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null) {
			fragmentRef.get().dismiss();
		} else {
			visible = false;
		}
	}

	public void setShowMenu() {
		showMenu = true;
	}

	private class RouteSpinnerRow {
		long id;
		int iconId;
		Drawable icon;
		String text;

		public RouteSpinnerRow(long id) {
			this.id = id;
		}

		public RouteSpinnerRow(long id, int iconId, String text) {
			this.id = id;
			this.iconId = iconId;
			this.text = text;
		}

		public RouteSpinnerRow(long id, Drawable icon, String text) {
			this.id = id;
			this.icon = icon;
			this.text = text;
		}
	}

	private class RouteBaseArrayAdapter extends ArrayAdapter<RouteSpinnerRow> {

		RouteBaseArrayAdapter(@NonNull Context context, int resource) {
			super(context, resource);
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public long getItemId(int position) {
			RouteSpinnerRow row = getItem(position);
			return row.id;
		}

		@Override
		public boolean isEnabled(int position) {
			long id = getItemId(position);
			return id != SPINNER_HINT_ID;
		}

		View getRowItemView(int position, View convertView, ViewGroup parent) {
			TextView label = (TextView) super.getView(position, convertView, parent);
			RouteSpinnerRow row = getItem(position);
			label.setText(row.text);
			label.setTextColor(!isLight() ?
					ContextCompat.getColorStateList(mapActivity, android.R.color.primary_text_dark) : ContextCompat.getColorStateList(mapActivity, android.R.color.primary_text_light));
			return label;
		}

		View getListItemView(int position, View convertView, ViewGroup parent) {
			long id = getItemId(position);
			TextView label = (TextView) super.getDropDownView(position, convertView, parent);

			RouteSpinnerRow row = getItem(position);
			label.setText(row.text);
			if (id != SPINNER_HINT_ID) {
				Drawable icon = null;
				if (row.icon != null) {
					icon = row.icon;
				} else if (row.iconId > 0) {
					icon = mapActivity.getMyApplication().getUIUtilities().getThemedIcon(row.iconId);
				}
				label.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
				label.setCompoundDrawablePadding(AndroidUtils.dpToPx(mapActivity, 16f));
			} else {
				label.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
				label.setCompoundDrawablePadding(0);
			}

			if (id == SPINNER_MAP_MARKER_MORE_ID) {
				label.setTextColor(!mapActivity.getMyApplication().getSettings().isLightContent() ?
						mapActivity.getResources().getColor(R.color.color_dialog_buttons_dark) : mapActivity.getResources().getColor(R.color.color_dialog_buttons_light));
			} else {
				label.setTextColor(!mapActivity.getMyApplication().getSettings().isLightContent() ?
						ContextCompat.getColorStateList(mapActivity, android.R.color.primary_text_dark) : ContextCompat.getColorStateList(mapActivity, android.R.color.primary_text_light));
			}
			label.setPadding(AndroidUtils.dpToPx(mapActivity, 16f), 0, 0, 0);

			return label;
		}
	}

	private class RouteSpinnerArrayAdapter extends RouteBaseArrayAdapter {

		RouteSpinnerArrayAdapter(Context context) {
			super(context, android.R.layout.simple_spinner_item);
			setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			return getRowItemView(position, convertView, parent);
		}

		@Override
		public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
			return getListItemView(position, convertView, parent);
		}
	}

	private class RoutePopupListArrayAdapter extends RouteBaseArrayAdapter {

		RoutePopupListArrayAdapter(Context context) {
			super(context, android.R.layout.simple_spinner_dropdown_item);
		}

		@NonNull
		@Override
		public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
			return getListItemView(position, convertView, parent);
		}
	}
}
