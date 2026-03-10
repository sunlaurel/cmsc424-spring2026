import java.sql.*;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;;

public class NearestNeighbor {
	static double jaccard(HashSet<String> s1, HashSet<String> s2) {
		int total_size = s1.size() + s2.size();
		int i_size = 0;
		for (String s : s1) {
			if (s2.contains(s))
				i_size++;
		}
		return ((double) i_size) / (total_size - i_size);
	}

	private static void parseTags(String[] tags, HashSet<String> tagSet) {
		Pattern p = Pattern.compile("<(?<tag>[^<>]+)>");

		for (String s : tags) {
			Matcher m = p.matcher(s);
			while (m.find()) {
				String g = m.group("tag");
				tagSet.add(g);
			}
		}
	}

	private static int findMaxSimilarity(HashMap<Integer, HashSet<String>> map, Integer s1) {
		double maxSimilarity = 0;
		int maxSimilarityTag = Integer.MAX_VALUE;
		for (int s2 : map.keySet()) {
			if (s1 != s2) {
				double tmp = jaccard(map.get(s1), map.get(s2));
				if (maxSimilarity < tmp || (maxSimilarity == tmp && s2 < maxSimilarityTag)) {
					maxSimilarityTag = s2;
					maxSimilarity = tmp;
				}
			}
		}

		return maxSimilarityTag;
	}

	public static void executeNearestNeighbor(Connection conn) {
		/*************
		 * Add your code to add a new column to the users table (set to null by
		 * default), calculate the nearest neighbor for each node (within first 5000),
		 * and write it back into the database for those users..
		 ************/
		try {

			// Preparing statements
			Statement stmt = conn.createStatement();
			PreparedStatement pstmt = conn.prepareStatement(
					"UPDATE users " +
							"SET nearest_neighbor = ? " +
							"WHERE users.id = ?");

			// Creating a new column
			String alter = "ALTER TABLE users " +
					"ADD COLUMN IF NOT EXISTS nearest_neighbor INT";
			stmt.execute(alter);

			// Fetching relevant data from database
			String fetch = "SELECT users.id, " +
					"array_remove(array_agg(posts.tags), null) as arr " +
					"from users, posts " +
					"where users.id = posts.owneruserid and users.id < 5000 " +
					"group by users.id " +
					"having count(posts.tags) > 0";
			ResultSet res = stmt.executeQuery(fetch);

			// Processing through all users and parsing tags to store in map
			HashMap<Integer, HashSet<String>> map = new HashMap<>();
			while (res.next()) {
				// Retrieving data from query
				Array t = res.getArray("arr");
				Integer id = res.getInt("id");
				String[] tags = (String[]) t.getArray();

				// Processing tags list to retrieve tags
				HashSet<String> tagSet = new HashSet<>();
				parseTags(tags, tagSet);
				map.put(id, tagSet);
			}

			for (int s1 : map.keySet()) {
				int maxSimilarityTag = findMaxSimilarity(map, s1);
				pstmt.setInt(1, maxSimilarityTag);
				pstmt.setInt(2, s1);
				pstmt.executeUpdate();
			}

			// Closing statements
			stmt.close();
			pstmt.close();

		} catch (SQLException s) {
			s.printStackTrace();
		}
	}

	public static void main(String[] argv) {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/stackexchange", "root", "root");
			executeNearestNeighbor(conn);
		} catch (SQLException s) {
			s.printStackTrace();
		} finally {
			try {
				conn.close();
			} catch (Throwable t) {
				System.out.println(t.getStackTrace());
			}
		}
	}
}
