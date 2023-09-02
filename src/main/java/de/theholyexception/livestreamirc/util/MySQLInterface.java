package de.theholyexception.livestreamirc.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.TomlTable;

import java.sql.*;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class MySQLInterface {

    private final String address;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    @Getter
    private Connection connection;

    private static final int RESULT_SET_TYPE = ResultSet.TYPE_SCROLL_SENSITIVE;
    private static final int RESULT_SET_CONCURRENCY = ResultSet.CONCUR_UPDATABLE;

    @Getter
    ExecutorHandler executorHandler;

    public MySQLInterface(TomlTable table) {
        this.executorHandler = new ExecutorHandler(Executors.newFixedThreadPool(2));
        this.address = table.getString("host");
        this.port = Math.toIntExact(Optional.ofNullable(table.getLong("port")).orElse(3306L));
        this.database = table.getString("databse");
        this.username = table.getString("username");
        this.password = table.getString("password");

        // Keep reconnecting
        new Timer().schedule(new TimerTask() {

            @Override
            public void run() {
                try {
                    if (connection.isClosed()) {
                        connect();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }, 10000, 10000);
    }

    public void connect() {
        try {
            log.info("Establishing MySQL Connection.");
            connection = DriverManager.getConnection(String.format("jdbc:mysql://%s:%s/%s", address, port, database), username, password);
            logServerInformation();
        } catch (Exception ex) {
            log.error("Connection Failed.");
            ex.printStackTrace();
        }
    }

    /**
     * Prints the information of the SQL Server
     */
    private void logServerInformation() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            log.info("Established Connection.");
            log.info("Driver name: {}", metaData.getDriverName());
            log.info("Driver version: {}", metaData.getDriverVersion());
            log.info("Product name: {}", metaData.getDatabaseProductName());
            log.info("Product version: {}", metaData.getDatabaseProductVersion());
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Executes an SQL Query
     * @param query Query that should be executed
     * @return ResultSet when successful
     */
    public ResultSet executeQuery(String query) {
        ResultSet resultSet = null;
        try {
            Statement statement = connection.createStatement(RESULT_SET_TYPE, RESULT_SET_CONCURRENCY);
            resultSet = statement.executeQuery(query);
            statement.closeOnCompletion();
        } catch (SQLException ex) {
            log.error("Failed to execute query");
            log.error(ex.getMessage());
        }
        return resultSet;
    }

    /**
     * Executes an SQL Query asynchronously
     * @param result ResultSet when successful
     * @param query Query that should be executed
     */
    public void executeQueryAsync(Consumer<ResultSet> result, String query) {
        executorHandler.putTask(new ExecutorTask(() -> result.accept(executeQuery(query))));
    }

}
