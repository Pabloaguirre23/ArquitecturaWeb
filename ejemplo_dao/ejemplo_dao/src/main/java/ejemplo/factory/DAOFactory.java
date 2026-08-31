package ejemplo.factory;

import java.sql.Connection;

import ejemplo.dao.ClienteDAO;
import ejemplo.dao.FacturaDAO;
import ejemplo.dao.FacturaProductoDAO;

import ejemplo.dao.ProductoDAO;
import ejemplo.repository.mysql.MySQLDAOFactory;

    public abstract class DAOFactory {

        private static volatile DAOFactory instance;

        public static DAOFactory getInstance(DBType type) {
            if (instance == null) {
                synchronized (DAOFactory.class) {
                    if (instance == null) {
                        switch (type) {
                            case MYSQL:
                                instance = new MySQLDAOFactory();
                                break;

                            //case DERBY:
                            //    instance = new DerbyDAOFactory();
                            //    break;
                            default:
                                throw new IllegalArgumentException("DBType no soportado: " + type);
                        }
                    }
                }
            }
            return instance;
        }

        public static DAOFactory getInstance() {
            String v = System.getProperty("db.type", "MYSQL");
            DBType type = DBType.valueOf(v.toUpperCase());
            return getInstance(type);
        }

        public abstract ProductoDAO createUsuarioDAO();
        public abstract FacturaProductoDAO createFacturaProductoDAO();
        public abstract FacturaDAO createFacturaDAO();
        public abstract ClienteDAO createClienteDAO();

        protected abstract Connection getConnection();

        public final void shutdown() {
            doShutdown();
            synchronized (DAOFactory.class) {
                instance = null;
            }
        }

        protected abstract void doShutdown();
    }
