package net.osmand.util;

import net.osmand.data.LatLon;
import java.util.ArrayList;
import java.util.List;

public class GeoPolylineParserUtil {
	/**
	 * Parses Google esque polyline
	 *
	 * @param encoded The polyline as a String
	 * @return {@link List<LatLon>}
	 */
	public static List<LatLon> parse(final String encoded) {
		List<LatLon> track = new ArrayList<LatLon>();
		int index = 0;
		int lat = 0, lng = 0;
		double precision = 1E5;

		while (index < encoded.length()) {
			int b, shift = 0, result = 0;
			do {
				b = encoded.charAt(index++) - 63;
				result |= (b & 0x1f) << shift;
				shift += 5;
			} while (b >= 0x20);
			int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
			lat += dlat;

			shift = 0;
			result = 0;
			do {
				b = encoded.charAt(index++) - 63;
				result |= (b & 0x1f) << shift;
				shift += 5;
			} while (b >= 0x20);
			int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
			lng += dlng;

			LatLon p = new LatLon((double) lat / precision, (double) lng / precision);
			track.add(p);
		}
		return track;
	}
}
