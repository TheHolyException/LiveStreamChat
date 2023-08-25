package de.theholyexception.livestreamirc.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
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

    private final int resultSetType        = ResultSet.TYPE_SCROLL_SENSITIVE;
    private final int resultSetConcurrency = ResultSet.CONCUR_UPDATABLE;
    @Getter
    ExecutorHandler executorHandler;

    public MySQLInterface(String address, int port, String username, String password, String database) {
        this.executorHandler = new ExecutorHandler(Executors.newFixedThreadPool(2));
        this.address = address;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;

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
            Statement statement = connection.createStatement(resultSetType, resultSetConcurrency);
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
