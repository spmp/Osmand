package net.osmand.plus.mapcontextmenu.other;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import net.osmand.AndroidUtils;
import net.osmand.CallbackWithObject;
import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuItem;
import net.osmand.plus.GPXUtilities;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.SettingsBaseActivity;
import net.osmand.plus.activities.SettingsNavigationActivity;
import net.osmand.plus.base.MenuBottomSheetDialogFragment;
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem;
import net.osmand.plus.base.bottomsheetmenu.BottomSheetItemWithCompoundButton;
import net.osmand.plus.base.bottomsheetmenu.BottomSheetItemWithDescription;
import net.osmand.plus.base.bottomsheetmenu.SimpleBottomSheetItem;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.DividerHalfItem;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.TitleItem;
import net.osmand.plus.dashboard.DashboardOnMap;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.routing.RouteProvider;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.views.MapControlsLayer;
import net.osmand.router.GeneralRouter;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static net.osmand.plus.activities.SettingsNavigationActivity.getRouter;

public class CarRouteOptionsBottomSheet extends MenuBottomSheetDialogFragment {

	public static final String TAG = "CarRouteOptionsBottomSheet";

	private OsmandSettings settings;
	private OsmandApplication app;
	private MapActivity mapActivity;
	private MapControlsLayer controlsLayer;
	private RoutingHelper routingHelper;
	private ApplicationMode applicationMode;

	private List<GeneralRouter.RoutingParameter> avoidParameters = new ArrayList<GeneralRouter.RoutingParameter>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = getMyApplication();
		settings = app.getSettings();
		routingHelper = app.getRoutingHelper();
		mapActivity = getMapActivity();
		controlsLayer = mapActivity.getMapLayers().getMapControlsLayer();
		applicationMode = routingHelper.getAppMode();
		prepareRoutingPrefs();
	}

	@Override
	public void createMenuItems(Bundle savedInstanceState) {
		items.add(new TitleItem(app.getString(R.string.shared_string_settings), nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light));

		List<OptionsItem> list = getRoutingParameters(applicationMode);
		for (OptionsItem optionsItem : list) {

			if (optionsItem instanceof DividerItem) {
				items.add(new DividerHalfItem(app));
			} else if (optionsItem instanceof MuteSoundRoutingParameter) {
				final BottomSheetItemWithCompoundButton[] MuteSoundRoutingParameter = new BottomSheetItemWithCompoundButton[1];
				MuteSoundRoutingParameter[0] = (BottomSheetItemWithCompoundButton) new BottomSheetItemWithCompoundButton.Builder()
						.setChecked(!routingHelper.getVoiceRouter().isMute())
						.setDescription(getString(R.string.voice_announcements))
						.setIcon(getContentIcon(R.drawable.ic_action_volume_up))
						.setTitle(getString(R.string.shared_string_sound))
						.setLayoutId(R.layout.bottom_sheet_item_with_descr_and_switch_56dp)
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								boolean mt = !routingHelper.getVoiceRouter().isMute();
								settings.VOICE_MUTE.set(mt);
								routingHelper.getVoiceRouter().setMute(mt);
								MuteSoundRoutingParameter[0].setChecked(!routingHelper.getVoiceRouter().isMute());
							}
						})
						.create();
				items.add(MuteSoundRoutingParameter[0]);

			} else if (optionsItem instanceof ShowAlongTheRouteItem) {
				BaseBottomSheetItem ShowAlongTheRouteItem = new SimpleBottomSheetItem.Builder()
						.setIcon(getContentIcon(R.drawable.ic_action_snap_to_road))
						.setTitle(getString(R.string.show_along_the_route))
						.setLayoutId(R.layout.bottom_sheet_item_simple)
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								Toast.makeText(app, getText(R.string.show_along_the_route), Toast.LENGTH_LONG).show();
							}
						})
						.create();
				items.add(ShowAlongTheRouteItem);
			} else if (optionsItem instanceof RouteSimulationItem) {
				BaseBottomSheetItem RouteSimulationItem = new SimpleBottomSheetItem.Builder()
						.setIcon(getContentIcon(R.drawable.ic_action_start_navigation))
						.setTitle(getString(R.string.simulate_navigation))
						.setLayoutId(R.layout.bottom_sheet_item_simple)
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								final OsmAndLocationProvider loc = app.getLocationProvider();
								loc.getLocationSimulation().startStopRouteAnimation(getActivity());
								dismiss();
							}
						})
						.create();
				items.add(RouteSimulationItem);
			} else if (optionsItem instanceof AvoidRoadsTypesRoutingParameter) {
				BaseBottomSheetItem AvoidRoadsRoutingParameter = new SimpleBottomSheetItem.Builder()
						.setIcon(getContentIcon(R.drawable.ic_action_road_works_dark))
						.setTitle(getString(R.string.impassable_road))
						.setLayoutId(R.layout.bottom_sheet_item_simple)
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								String[] vals = new String[avoidParameters.size()];
								OsmandSettings.OsmandPreference[] bls = new OsmandSettings.OsmandPreference[avoidParameters.size()];
								for (int i = 0; i < avoidParameters.size(); i++) {
									GeneralRouter.RoutingParameter p = avoidParameters.get(i);
									vals[i] = SettingsBaseActivity.getRoutingStringPropertyName(app, p.getId(), p.getName());
									bls[i] = settings.getCustomRoutingBooleanProperty(p.getId(), p.getDefaultBoolean());
								}
								showBooleanSettings(vals, bls, getString(R.string.impassable_road));
							}
						})
						.create();
				items.add(AvoidRoadsRoutingParameter);
			} else if (optionsItem instanceof AvoidRoadsRoutingParameter) {
				BaseBottomSheetItem AvoidRoadsRoutingParameter = new SimpleBottomSheetItem.Builder()
						.setIcon(getContentIcon(R.drawable.ic_action_road_works_dark))
						.setTitle(getString(R.string.impassable_road))
						.setLayoutId(R.layout.bottom_sheet_item_simple)
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								mapActivity.getDashboard().setDashboardVisibility(false, DashboardOnMap.DashboardType.ROUTE_PREFERENCES);
								controlsLayer.getMapRouteInfoMenu().hide();
								app.getAvoidSpecificRoads().showDialog(mapActivity);
								dismiss();
							}
						})
						.create();
				items.add(AvoidRoadsRoutingParameter);

			} else if (optionsItem instanceof GpxLocalRoutingParameter) {
				View v = mapActivity.getLayoutInflater().inflate(R.layout.plan_route_gpx, null);
				AndroidUtils.setListItemBackground(mapActivity, v, nightMode);
				AndroidUtils.setTextPrimaryColor(mapActivity, (TextView) v.findViewById(R.id.GPXRouteTitle), nightMode);
				final TextView gpxSpinner = (TextView) v.findViewById(R.id.GPXRouteSpinner);
				AndroidUtils.setTextPrimaryColor(mapActivity, gpxSpinner, nightMode);
				((ImageView) v.findViewById(R.id.dropDownIcon))
						.setImageDrawable(app.getUIUtilities().getIcon(R.drawable.ic_action_arrow_drop_down, !nightMode));

				BaseBottomSheetItem GpxLocalRoutingParameter = new BottomSheetItemWithDescription.Builder()
						.setDescription(getString(R.string.choose_track_file_to_follow))
						.setIcon(getContentIcon(R.drawable.ic_action_polygom_dark))
						.setTitle(getString(R.string.shared_string_gpx_route))
						.setLayoutId(R.layout.bottom_sheet_item_with_descr_56dp)
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								openGPXFileSelection();
							}
						})
						.create();
				items.add(GpxLocalRoutingParameter);

			} else if (optionsItem instanceof OtherSettingsRoutingParameter) {
				BaseBottomSheetItem OtherSettingsRoutingParameter = new SimpleBottomSheetItem.Builder()
						.setIcon(getContentIcon(R.drawable.map_action_settings))
						.setTitle(getString(R.string.routing_settings_2))
						.setLayoutId(R.layout.bottom_sheet_item_simple)
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								final Intent settings = new Intent(mapActivity, SettingsNavigationActivity.class);
								settings.putExtra(SettingsNavigationActivity.INTENT_SKIP_DIALOG, true);
								settings.putExtra(SettingsBaseActivity.INTENT_APP_MODE, routingHelper.getAppMode().getStringKey());
								mapActivity.startActivity(settings);
							}
						})
						.create();
				items.add(OtherSettingsRoutingParameter);

			} else {
				inflateRoutingParameter(optionsItem);
			}
		}
	}

	@Override
	protected int getDismissButtonTextId() {
		return R.string.shared_string_close;
	}

	private void prepareRoutingPrefs() {
		GeneralRouter router = getRouter(app.getDefaultRoutingConfig(), applicationMode);
		if (router != null) {
			for (Map.Entry<String, GeneralRouter.RoutingParameter> e : router.getParameters().entrySet()) {
				String param = e.getKey();
				GeneralRouter.RoutingParameter routingParameter = e.getValue();
				if (param.startsWith("avoid_")) {
					avoidParameters.add(routingParameter);
				}
			}
		}
	}


	public AlertDialog showBooleanSettings(String[] vals, final OsmandSettings.OsmandPreference<Boolean>[] prefs, final CharSequence title) {
		AlertDialog.Builder bld = new AlertDialog.Builder(mapActivity);
		boolean[] checkedItems = new boolean[prefs.length];
		for (int i = 0; i < prefs.length; i++) {
			checkedItems[i] = prefs[i].get();
		}

		final boolean[] tempPrefs = new boolean[prefs.length];
		for (int i = 0; i < prefs.length; i++) {
			tempPrefs[i] = prefs[i].get();
		}

		bld.setMultiChoiceItems(vals, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked) {
				tempPrefs[which] = isChecked;
			}
		});

		bld.setTitle(title);

		bld.setNegativeButton(R.string.shared_string_cancel, null);

		bld.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				for (int i = 0; i < prefs.length; i++) {
					prefs[i].set(tempPrefs[i]);
				}
			}
		});

		return bld.show();
	}

	private void inflateRoutingParameter(final OptionsItem optionsItem) {
		if (optionsItem instanceof LocalRoutingParameter) {
			final LocalRoutingParameter parameter = (LocalRoutingParameter) optionsItem;
			final BottomSheetItemWithCompoundButton[] item = new BottomSheetItemWithCompoundButton[1];
			BottomSheetItemWithCompoundButton.Builder builder = new BottomSheetItemWithCompoundButton.Builder();
			builder.setIcon(getContentIcon(R.drawable.mx_amenity_fuel));
			if (parameter.routingParameter != null) {
				builder.setTitle(parameter.getText(mapActivity));
			}
			if (parameter instanceof LocalRoutingParameterGroup) {
				final LocalRoutingParameterGroup group = (LocalRoutingParameterGroup) parameter;
				LocalRoutingParameter selected = group.getSelected(settings);
				if (selected != null) {
					builder.setTitle(group.getText(mapActivity));
					builder.setDescription(selected.getText(mapActivity));
				}
				builder.setLayoutId(R.layout.bottom_sheet_item_with_descr_56dp);
				builder.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						final ContextMenuAdapter adapter = new ContextMenuAdapter();
						int i = 0;
						int selectedIndex = -1;
						for (LocalRoutingParameter p : group.getRoutingParameters()) {
							adapter.addItem(ContextMenuItem.createBuilder(p.getText(mapActivity))
									.setSelected(false).createItem());
							if (p.isSelected(settings)) {
								selectedIndex = i;
							}
							i++;
						}
						if (selectedIndex == -1) {
							selectedIndex = 0;
						}

						AlertDialog.Builder builder = new AlertDialog.Builder(mapActivity);
						final int layout = R.layout.list_menu_item_native_singlechoice;

						final ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(mapActivity, layout, R.id.text1,
								adapter.getItemNames()) {
							@NonNull
							@Override
							public View getView(final int position, View convertView, ViewGroup parent) {
								// User super class to create the View
								View v = convertView;
								if (v == null) {
									v = mapActivity.getLayoutInflater().inflate(layout, null);
								}
								final ContextMenuItem item = adapter.getItem(position);
								TextView tv = (TextView) v.findViewById(R.id.text1);
								tv.setText(item.getTitle());
								tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);

								return v;
							}
						};

						final int[] selectedPosition = {selectedIndex};
						builder.setSingleChoiceItems(listAdapter, selectedIndex, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int position) {
								selectedPosition[0] = position;
							}
						});
						builder.setTitle(group.getText(mapActivity))
								.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog, int which) {
										int position = selectedPosition[0];
										if (position >= 0 && position < group.getRoutingParameters().size()) {
											for (int i = 0; i < group.getRoutingParameters().size(); i++) {
												LocalRoutingParameter rp = group.getRoutingParameters().get(i);
												rp.setSelected(settings, i == position);
											}
											mapActivity.getRoutingHelper().recalculateRouteDueToSettingsChange();
											LocalRoutingParameter selected = group.getSelected(settings);
											if (selected != null) {
												item[0].setDescription(selected.getText(mapActivity));
											}
										}
									}
								})
								.setNegativeButton(R.string.shared_string_cancel, null);

						builder.create().show();
					}
				});
			} else {
				builder.setLayoutId(R.layout.bottom_sheet_item_with_switch);
				if (parameter.routingParameter != null) {
					if (parameter.routingParameter.getId().equals("short_way")) {
						// if short route settings - it should be inverse of fast_route_mode
						builder.setChecked(!settings.FAST_ROUTE_MODE.getModeValue(routingHelper.getAppMode()));
					} else {
						builder.setChecked(parameter.isSelected(settings));
					}
				}
				builder.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						boolean selected = parameter.isSelected(settings);
						applyRoutingParameter(parameter, !selected);
						item[0].setChecked(!selected);
					}
				});
			}
			item[0] = builder.create();
			items.add(item[0]);
		}
	}

	private void applyRoutingParameter(LocalRoutingParameter rp, boolean isChecked) {
		// if short way that it should set valut to fast mode opposite of current
		if (rp.routingParameter != null && rp.routingParameter.getId().equals("short_way")) {
			settings.FAST_ROUTE_MODE.setModeValue(routingHelper.getAppMode(), !isChecked);
		}
		rp.setSelected(settings, isChecked);

		if (rp instanceof OtherLocalRoutingParameter) {
			updateGpxRoutingParameter((OtherLocalRoutingParameter) rp);
		}
		mapActivity.getRoutingHelper().recalculateRouteDueToSettingsChange();
	}

	private void updateGpxRoutingParameter(OtherLocalRoutingParameter gpxParam) {
		RouteProvider.GPXRouteParamsBuilder rp = mapActivity.getRoutingHelper().getCurrentGPXRoute();
		boolean selected = gpxParam.isSelected(settings);
		if (rp != null) {
			if (gpxParam.id == R.string.gpx_option_reverse_route) {
				rp.setReverse(selected);
				TargetPointsHelper tg = app.getTargetPointsHelper();
				List<Location> ps = rp.getPoints();
				if (ps.size() > 0) {
					Location first = ps.get(0);
					Location end = ps.get(ps.size() - 1);
					TargetPointsHelper.TargetPoint pn = tg.getPointToNavigate();
					boolean update = false;
					if (pn == null
							|| MapUtils.getDistance(pn.point, new LatLon(first.getLatitude(), first.getLongitude())) < 10) {
						tg.navigateToPoint(new LatLon(end.getLatitude(), end.getLongitude()), false, -1);
						update = true;
					}
					if (tg.getPointToStart() == null
							|| MapUtils.getDistance(tg.getPointToStart().point,
							new LatLon(end.getLatitude(), end.getLongitude())) < 10) {
						tg.setStartPoint(new LatLon(first.getLatitude(), first.getLongitude()), false, null);
						update = true;
					}
					if (update) {
						tg.updateRouteAndRefresh(true);
					}
				}
			} else if (gpxParam.id == R.string.gpx_option_calculate_first_last_segment) {
				rp.setCalculateOsmAndRouteParts(selected);
				settings.GPX_ROUTE_CALC_OSMAND_PARTS.set(selected);
			} else if (gpxParam.id == R.string.gpx_option_from_start_point) {
				rp.setPassWholeRoute(selected);
			} else if (gpxParam.id == R.string.use_points_as_intermediates) {
				settings.GPX_CALCULATE_RTEPT.set(selected);
				rp.setUseIntermediatePointsRTE(selected);
			} else if (gpxParam.id == R.string.calculate_osmand_route_gpx) {
				settings.GPX_ROUTE_CALC.set(selected);
				rp.setCalculateOsmAndRoute(selected);
			}
		}
		if (gpxParam.id == R.string.calculate_osmand_route_without_internet) {
			settings.GPX_ROUTE_CALC_OSMAND_PARTS.set(selected);
		}
		if (gpxParam.id == R.string.fast_route_mode) {
			settings.FAST_ROUTE_MODE.set(selected);
		}
		if (gpxParam.id == R.string.speak_favorites) {
			settings.ANNOUNCE_NEARBY_FAVORITES.set(selected);
		}
	}

	private MapActivity getMapActivity() {
		return (MapActivity) getActivity();
	}

	public static void showInstance(FragmentManager fragmentManager) {
		CarRouteOptionsBottomSheet f = new CarRouteOptionsBottomSheet();
		f.show(fragmentManager, CarRouteOptionsBottomSheet.TAG);
	}

	protected void openGPXFileSelection() {
		GpxUiHelper.selectGPXFile(mapActivity, false, false, new CallbackWithObject<GPXUtilities.GPXFile[]>() {

			@Override
			public boolean processResult(GPXUtilities.GPXFile[] result) {
				mapActivity.getMapActions().setGPXRouteParams(result[0]);
				app.getTargetPointsHelper().updateRouteAndRefresh(true);
				routingHelper.recalculateRouteDueToSettingsChange();
				return true;
			}
		});
	}

	private List<OptionsItem> getRoutingParameters(ApplicationMode am) {
		List<OptionsItem> list = new ArrayList<>();
		if (am.equals(ApplicationMode.CAR)) {
			getAppModeItems(am, list, AppModeOptions.CAR.routingParameters);
		} else if (am.equals(ApplicationMode.BICYCLE)) {
			getAppModeItems(am, list, AppModeOptions.BICYCLE.routingParameters);
		} else if (am.equals(ApplicationMode.PEDESTRIAN)) {
			getAppModeItems(am, list, AppModeOptions.PEDESTRIAN.routingParameters);
		}
		return list;
	}

	private List<OptionsItem> getAppModeItems(ApplicationMode am, List<OptionsItem> list, List<String> routingParameters) {
		for (String itemId : routingParameters) {
			OptionsItem item = getItem(am, itemId);
			if (item != null) {
				list.add(item);
			}
		}
		return list;
	}

	private OptionsItem getItem(ApplicationMode am, String parameter) {
		switch (parameter) {
			case MuteSoundRoutingParameter.KEY:
				return new MuteSoundRoutingParameter();
			case DividerItem.KEY:
				return new DividerItem();
			case RouteSimulationItem.KEY:
				return new RouteSimulationItem();
			case ShowAlongTheRouteItem.KEY:
				return new ShowAlongTheRouteItem();
			case AvoidRoadsTypesRoutingParameter.KEY:
				return new AvoidRoadsTypesRoutingParameter();
			case AvoidRoadsRoutingParameter.KEY:
				return new AvoidRoadsRoutingParameter();
			case GpxLocalRoutingParameter.KEY:
				return new GpxLocalRoutingParameter();
			case OtherSettingsRoutingParameter.KEY:
				return new OtherSettingsRoutingParameter();
			default:
				return getRoutingParametersInner(am, parameter);
		}
	}

	private OptionsItem getRoutingParametersInner(ApplicationMode am, String parameter) {
		RouteProvider.GPXRouteParamsBuilder rparams = mapActivity.getRoutingHelper().getCurrentGPXRoute();
		GeneralRouter rm = getRouter(app.getDefaultRoutingConfig(), am);
		if (rm == null || (rparams != null && !rparams.isCalculateOsmAndRoute()) && !rparams.getFile().hasRtePt()) {
			return null;
		}

		LocalRoutingParameter rp;
		Map<String, GeneralRouter.RoutingParameter> parameters = rm.getParameters();
		GeneralRouter.RoutingParameter routingParameter = parameters.get(parameter);

		if (routingParameter != null) {
			rp = new LocalRoutingParameter(am);
			rp.routingParameter = routingParameter;
		} else {
			LocalRoutingParameterGroup rpg = null;
			for (GeneralRouter.RoutingParameter r : rm.getParameters().values()) {
				if (r.getType() == GeneralRouter.RoutingParameterType.BOOLEAN
						&& !Algorithms.isEmpty(r.getGroup()) && r.getGroup().equals(parameter)) {
					if (rpg == null) {
						rpg = new LocalRoutingParameterGroup(am, r.getGroup());
					}
					rpg.addRoutingParameter(r);
				}
			}
			return rpg;
		}

		return rp;
	}


	private LocalRoutingParameterGroup getLocalRoutingParameterGroup(List<LocalRoutingParameter> list, String groupName) {
		for (LocalRoutingParameter p : list) {
			if (p instanceof LocalRoutingParameterGroup && groupName.equals(((LocalRoutingParameterGroup) p).getGroupName())) {
				return (LocalRoutingParameterGroup) p;
			}
		}
		return null;
	}

	public static class OptionsItem {

		public static final String KEY = "OptionsItem";

		public ApplicationMode am;

		public OptionsItem(ApplicationMode am) {
			this.am = am;
		}
	}

	public static class LocalRoutingParameter extends OptionsItem {

		public static final String KEY = "LocalRoutingParameter";

		public GeneralRouter.RoutingParameter routingParameter;

		public LocalRoutingParameter(ApplicationMode am) {
			super(am);
		}

		public String getText(MapActivity mapActivity) {
			return SettingsBaseActivity.getRoutingStringPropertyName(mapActivity, routingParameter.getId(),
					routingParameter.getName());
		}

		public boolean isSelected(OsmandSettings settings) {
			final OsmandSettings.CommonPreference<Boolean> property =
					settings.getCustomRoutingBooleanProperty(routingParameter.getId(), routingParameter.getDefaultBoolean());
			if (am != null) {
				return property.getModeValue(am);
			} else {
				return property.get();
			}
		}

		public void setSelected(OsmandSettings settings, boolean isChecked) {
			final OsmandSettings.CommonPreference<Boolean> property =
					settings.getCustomRoutingBooleanProperty(routingParameter.getId(), routingParameter.getDefaultBoolean());
			if (am != null) {
				property.setModeValue(am, isChecked);
			} else {
				property.set(isChecked);
			}
		}

		public ApplicationMode getApplicationMode() {
			return am;
		}
	}

	public static class LocalRoutingParameterGroup extends LocalRoutingParameter {

		public static final String KEY = "LocalRoutingParameterGroup";

		private String groupName;
		private List<LocalRoutingParameter> routingParameters = new ArrayList<>();

		public LocalRoutingParameterGroup(ApplicationMode am, String groupName) {
			super(am);
			this.groupName = groupName;
		}

		public void addRoutingParameter(GeneralRouter.RoutingParameter routingParameter) {
			LocalRoutingParameter p = new LocalRoutingParameter(getApplicationMode());
			p.routingParameter = routingParameter;
			routingParameters.add(p);
		}

		public String getGroupName() {
			return groupName;
		}

		public List<LocalRoutingParameter> getRoutingParameters() {
			return routingParameters;
		}

		@Override
		public String getText(MapActivity mapActivity) {
			return SettingsBaseActivity.getRoutingStringPropertyName(mapActivity, groupName,
					Algorithms.capitalizeFirstLetterAndLowercase(groupName.replace('_', ' ')));
		}

		@Override
		public boolean isSelected(OsmandSettings settings) {
			return false;
		}

		@Override
		public void setSelected(OsmandSettings settings, boolean isChecked) {
		}

		public LocalRoutingParameter getSelected(OsmandSettings settings) {
			for (LocalRoutingParameter p : routingParameters) {
				if (p.isSelected(settings)) {
					return p;
				}
			}
			return null;
		}
	}

	public static class MuteSoundRoutingParameter extends LocalRoutingParameter {

		public static final String KEY = "MuteSoundRoutingParameter";

		public MuteSoundRoutingParameter() {
			super(null);
		}
	}

	public static class DividerItem extends LocalRoutingParameter {

		public static final String KEY = "DividerItem";

		public DividerItem() {
			super(null);
		}
	}

	public static class RouteSimulationItem extends LocalRoutingParameter {

		public static final String KEY = "RouteSimulationItem";

		public RouteSimulationItem() {
			super(null);
		}
	}

	public static class ShowAlongTheRouteItem extends LocalRoutingParameter {

		public static final String KEY = "ShowAlongTheRouteItem";

		public ShowAlongTheRouteItem() {
			super(null);
		}
	}

	public static class AvoidRoadsRoutingParameter extends LocalRoutingParameter {

		public static final String KEY = "AvoidRoadsRoutingParameter";

		public AvoidRoadsRoutingParameter() {
			super(null);
		}

	}

	public static class AvoidRoadsTypesRoutingParameter extends LocalRoutingParameter {

		public static final String KEY = "AvoidRoadsTypesRoutingParameter";

		public AvoidRoadsTypesRoutingParameter() {
			super(null);
		}

	}

	public static class GpxLocalRoutingParameter extends LocalRoutingParameter {

		public static final String KEY = "GpxLocalRoutingParameter";

		public GpxLocalRoutingParameter() {
			super(null);
		}
	}

	public static class OtherSettingsRoutingParameter extends LocalRoutingParameter {

		public static final String KEY = "OtherSettingsRoutingParameter";

		public OtherSettingsRoutingParameter() {
			super(null);
		}
	}

	public static class OtherLocalRoutingParameter extends LocalRoutingParameter {

		public static final String KEY = "OtherLocalRoutingParameter";

		public String text;
		public boolean selected;
		public int id;

		public OtherLocalRoutingParameter(int id, String text, boolean selected) {
			super(null);
			this.text = text;
			this.selected = selected;
			this.id = id;
		}

		@Override
		public String getText(MapActivity mapActivity) {
			return text;
		}

		@Override
		public boolean isSelected(OsmandSettings settings) {
			return selected;
		}

		@Override
		public void setSelected(OsmandSettings settings, boolean isChecked) {
			selected = isChecked;
		}
	}

	public enum AppModeOptions {

		CAR(MuteSoundRoutingParameter.KEY,
				DividerItem.KEY,
				AvoidRoadsRoutingParameter.KEY,
				ShowAlongTheRouteItem.KEY,
				GeneralRouter.ALLOW_PRIVATE,
				GeneralRouter.USE_SHORTEST_WAY,
				DividerItem.KEY,
				GpxLocalRoutingParameter.KEY,
				OtherSettingsRoutingParameter.KEY,
				RouteSimulationItem.KEY),

		BICYCLE(MuteSoundRoutingParameter.KEY,
				"driving_style",
				GeneralRouter.USE_HEIGHT_OBSTACLES,
				DividerItem.KEY,
				AvoidRoadsTypesRoutingParameter.KEY,
				ShowAlongTheRouteItem.KEY,
				DividerItem.KEY,
				GpxLocalRoutingParameter.KEY,
				OtherSettingsRoutingParameter.KEY,
				RouteSimulationItem.KEY),

		PEDESTRIAN(MuteSoundRoutingParameter.KEY,
				DividerItem.KEY,
				GeneralRouter.USE_HEIGHT_OBSTACLES,
				AvoidRoadsTypesRoutingParameter.KEY,
				ShowAlongTheRouteItem.KEY,
				DividerItem.KEY,
				GpxLocalRoutingParameter.KEY,
				OtherSettingsRoutingParameter.KEY,
				RouteSimulationItem.KEY);


		List<String> routingParameters;

		AppModeOptions(String... routingParameters) {
			this.routingParameters = Arrays.asList(routingParameters);
		}
	}
}