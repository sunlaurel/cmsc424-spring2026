import java.sql.*;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class MetaData 
{
	static String dataTypeName(int i) {
		switch (i) {
			case java.sql.Types.INTEGER: return "Integer";
			case java.sql.Types.REAL: return "Real";
			case java.sql.Types.VARCHAR: return "Varchar";
			case java.sql.Types.TIMESTAMP: return "Timestamp";
			case java.sql.Types.DATE: return "Date";
		}
		return "Other";
	}

	private static void printKeys(DatabaseMetaData dmd, String tableName) {
		// Processing the columns of the table
		HashMap<String, String> keyTypes = new HashMap<>();
		ArrayList<String> keys = new ArrayList<>();
		try (ResultSet cols = dmd.getColumns(null, null, tableName, null)) {
			while (cols.next()) {
				String columnName = cols.getString("COLUMN_NAME").toUpperCase();
				String dataType = dataTypeName(cols.getInt("DATA_TYPE"));
				keyTypes.put(columnName, dataType);
				keys.add(columnName);
			}

			// Sorting the keys and printing them out
			Collections.sort(keys);
			System.out.println(String.join(", ",
				keys.stream().
				map(k -> k + " (" + keyTypes.get(k) + ")")
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new))
			));

			// Getting the primary key
			ResultSet pkeys = dmd.getPrimaryKeys(null, null, tableName);
			ArrayList<String> primaryKeys = new ArrayList<>();
			while (pkeys.next()) {
				String primKeyName = pkeys.getString("COLUMN_NAME");
				primaryKeys.add(primKeyName.toUpperCase());
			}

			Collections.sort(primaryKeys);
			System.out.println("Primary Key: " + String.join(", ", primaryKeys));
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static ArrayList<String> getJoins(DatabaseMetaData dmd, String tableName) {
		ArrayList<String> joins = new ArrayList<>();
		try (ResultSet fkeys = dmd.getImportedKeys(null, null, tableName)) {
			while (fkeys.next()) {
				String pkTableName = fkeys.getString("PKTABLE_NAME").toUpperCase();
				String fkTableName = fkeys.getString("FKTABLE_NAME").toUpperCase();
				String pkColumnName = fkeys.getString("PKCOLUMN_NAME").toUpperCase();
				String fkColumnName = fkeys.getString("FKCOLUMN_NAME").toUpperCase();
				joins.add(String.format("%s can be joined %s on attributes %s and %s", pkTableName, fkTableName, pkColumnName, fkColumnName));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return joins;
	}

	public static void executeMetadata(String databaseName) {
		/************* 
		 * Add your code to connect to the database and print out the metadta for the database databaseName. 
		 ************/
		try {
			Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/" + databaseName, "root", "root");
			DatabaseMetaData dmd = conn.getMetaData();
			ResultSet tbs = dmd.getTables(null, null, null, new String[]{"TABLE"});
			System.out.println("### Tables in the Database");
			
			// Getting the joings
			ArrayList<String> joins = new ArrayList<>();

			while (tbs.next()) {
				String tableName = tbs.getString("TABLE_NAME");
				System.out.printf("-- Table %s\nAttributes: ", tableName.toUpperCase());
				
				printKeys(dmd, tableName);
				joins.addAll(getJoins(dmd, tableName));
			}
			
			System.out.println("\n### Joinable Pairs of Tables (based on Foreign Keys)");
			// Sorting and printing out possible joins
			Collections.sort(joins);
			for (String j: joins) {
				System.out.println(j);
			}
			
			conn.close();
		} catch (SQLException s) {
			s.printStackTrace();
		} 
	}

	public static void main(String[] argv) {
		executeMetadata(argv[0]);
	}
}
