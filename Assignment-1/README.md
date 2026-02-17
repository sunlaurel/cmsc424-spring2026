# Assignment 2: Advanced SQL, SQL + Programming Languages

**This assignment is to be done by yourself.**

## AI Use Policy
You are allowed to use AI tools to better understand the SQL constructs, or JDBC operations, etc., and to possibly get a first draft of the required code (ideally usng a chatbot). However, I would recommend against using AI coding assistants directly to fix the code, or to run the tests and debug. After getting the first draft, you should make sure you can understand the query/code and be able to test it and debug it yourself.

## Learning Goals
By completing this assignment, you will:

- **Advanced SQL:** Use PostgreSQL constructs such as ranking, functions, procedures, partitioning, triggers, and recursion, and learn to look them up in the PostgreSQL documentation.
- **SQL from code:** Connect to a database from a programming language (Java) using JDBC, run queries, and process result sets.
- **Data + computation:** Combine database queries with in-application logic (e.g., parsing, set operations, similarity metrics) when that is more natural than doing everything in SQL.
- **Similarity and neighbors:** Implement set-based similarity (Jaccard coefficient) and use it to find “nearest neighbor” users from tag data.
- **Database updates from applications:** Modify schema (e.g., add columns) and update rows from an application, and use transactions (e.g., commit) correctly.
- **Schema introspection with JDBC:** Use `DatabaseMetaData` (e.g., `getMetaData()`) to discover tables, columns, primary keys, and foreign keys, and use that information to summarize a database schema and infer join relationships.


## Submission
Upload the three files `queries.py`, `NearestNeighbor.java`, and `MetaData.java` to GradeScope.

## Advanced SQL Constructs (0.25 or 0.5 each -- total 4 points)
Here we experiment with some of the more advanced constructs that are available in SQL, including ranking, functions, procedures, partitioning, triggers, 
and recursion. Most of these were NOT covered in depth in class, and you are expected to read the PostgreSQL manual to understand how some of 
these constructs should be used. We have provided links to the appropriate PostgreSQL documentation and/or examples.

See `queries.py` for the details.

### Testing and submitting using SQLTesting.py
Your answers (i.e., SQL queries) should be added to the `queries.py` file. A simple query is provided for the first answer to show you how it works.
You are also provided with a Python file `SQLTesting.py` for testing your answers.

- We recommend that you use `psql` to design your queries, and then paste the queries to the `queries.py` file, and confirm it works.

- SQLTesting takes quite a few options: use `python3 SQLTesting.py -h` to see the options.

- To get started with SQLTesting, do: `python3 SQLTesting.py -i` -- that will run each of the queries and show you your answer.

- If you want to run your query for Question 1, use: `python3 SQLTesting.py -q 1`. 

- `-i` flag to SQLTesting will run all the queries, one at a time (waiting for you to press Enter after each query).

- `SQLTesting.py` has relatively complex logic. Many of the questions here are asking you to modify the database, and we need to test that those modifications are done properly. `SQLTesting.py` has specialized logic to test many of those things, so we would recommend going through the code to understand what it is doing.

- **Note**: We will essentially run a modified version of `SQLTesting.py` that compares the returned answers against correct answers. So it imperative that `python3 SQLTesting.py` runs without errors.

### Submission
Upload the file `queries.py`. Make sure `python3 SQLTesting.py` runs before submitting.

## Part 2: SQL and Java (2 point)
One of more prominent ways to use a database system is using an external client, using APIs such as ODBC and JDBC, or proprietary protocols.
This allows you to run queries against the database and access the results from within say a Java or a Python program.

For this part, you have to write/complete a Java program that accesses the database using JDBC, does some computations that are better done in a
programming langauge, and writes back the result to the database.

Here are some useful links:
- [Wikipedia Article](http://en.wikipedia.org/wiki/Java_Database_Connectivity)
- [Another resource](http://www.mkyong.com/java/how-do-connect-to-postgresql-with-jdbc-driver-java/)
- [PostgreSQL JDBC](http://jdbc.postgresql.org/index.html)

The last link has detailed examples in the `documentation` section. The `Assignment-2` directory (in the git repository) also contains an example 
file (`JDBCExample.java`). To run the JDBCExample.java file, do: 
`javac JDBCExample.java` followed by `java -classpath .:./postgresql-42.2.10.jar JDBCExample` (the `jar` file is in the `Assignment-2` directory).

Our goal is to find, for each user, its `nearest neighbor` based on the tags of the posts that they have made (defined more formally below). We will ignore posts where `OwnerUserId` is Null.

The simple algorithm we have listed below wouldn't work for the number of users we have, so we will only focus on the first 1000 users.
Here are the main steps:

1. Execute an SQL statement to add a new column to the `users` table called `nearest_neighbor`. This should be an `integer` column.
1. Use the following query to fetch relevant data from the database: 
```
select users.id, array_remove(array_agg(posts.tags), null) as arr
from users, posts 
where users.id = posts.OwnerUserId and users.id < 5000 
group by users.id
having count(posts.tags) > 0;
```
This will give us, for each user, an array of `tags` strings (which themselves are lists). Users with no posts with tags will be omitted.
1. Parse and separate out the tags for each user, so that, for each userid, we get a set of tags (use Java HashSet to store these).
1. For each user, say ID = A, go through the rest of the users and find the user with the highest `Jaccard Similarity Coefficient` based on the tag sets for the two users.
    1. Given two sets of tags, S1 and S2, the Jaccard Similarly is computed as: (size of the intersection of S1 and S2)/(size of the union of S1 and S2)
    1. We have provided you with a function (`jaccard`) that takes in two HashSets of Strings and return the Jaccard Coefficient.
1. If there is a tie (say B and C both have the same Jaccard Similarity with A), you should use the user with the lowest ID (i.e., use B if B < C).
1. Write out the id of the closest user computed above to the `nearest_neighbor` column for A.
1. Make sure to commit after you are done.

First rows of `select * from users order by id limit 10` afterwards look like:
```
 id | reputation | creationdate |   displayname   | views | upvotes | downvotes | nearest_neighbor
----+------------+--------------+-----------------+-------+---------+-----------+------------------
 -1 |          1 | 2011-01-03   | Community       |   863 |   12299 |      8651 |
  2 |        101 | 2011-01-03   | Geoff Dalgas    |    64 |      10 |         0 |
  3 |        101 | 2011-01-03   | balpha          |    35 |       4 |         0 |
  4 |        212 | 2011-01-03   | Nick Craver     |    61 |      11 |         1 |
  5 |        101 | 2011-01-03   | Emmett          |    32 |       2 |         0 |
  6 |        100 | 2011-01-03   | Robert Cartaino |    36 |       0 |         2 |
  7 |       1128 | 2011-01-03   | Toby            |    69 |      18 |         0 |             2993
  8 |       2949 | 2011-01-03   | ilhan           |   142 |      12 |         0 |               14
  9 |        101 | 2011-01-03   | Humpton         |    12 |       7 |         0 |
 10 |        175 | 2011-01-03   | Kim             |    27 |       1 |         1 |
(10 rows)
```

For most of these, the `nearest_neighbor` is null because they don't satisfy the condition listed above (that there be at least one post with tags for that user).

We have provided a skeleton code to get you started. As above, the code will be run using: `javac NearestNeighbor.java` followed by `java -classpath
.:./postgresql-42.2.10.jar NearestNeighbor`, and should result in a modified `users` table as described above.

Some resources:
- https://docs.oracle.com/javase/tutorial/jdbc/basics/array.html discusses how to retrieve and operate upons Arrays using JDBC.


## Part 3: Metadata Operations with JDBC (1 point)
JDBC also allows inspection of the schema, which can be a powerful feature to explore new databases (or datasets). Here, your task is to use that functionality to create a short summary of the tables in a database and the possible joins between the tables of the database based on the `foreign keys`.

Specifically, you should use the `getMetaData()` function to obtain `DatabaseMetaData` object, and use that to fetch information about the tables in the database as well as information about the primary keys and foreign keys. 

This resource here has detailed examples of how to use this functionality: https://www.baeldung.com/jdbc-database-metadata

We have provided a skeleton file, `MetaData.java` -- your task is to complete the function within.

For the expected output format, see the files `exampleOutputMetadataStackexchange.txt` and `exampleOutputMetadataUniversity.txt`, which should be the output of running the program on our `stackexchange` and `university` databases. The overall order of tables and joinable relationships doesn't matter -- however, the lists of attributes (for a table and for a primary key) should be sorted in the increasing order (use `Collections.sort()`).

Notes/Hints:
1. Use `.toUpperCase()` to convert table/attribute names to uppper case.
1. We have provided a function to map the integer `type` that JDBC uses to a String, that you can use when printing out the data type of a column.
1. Use `Collections.sot()` to sort the attribute lists.

