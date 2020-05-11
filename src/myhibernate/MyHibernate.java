package myhibernate;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import Entities.Producto;
import ann.Column;
import ann.Id;
import ann.JoinColumn;
import ann.Table;
import database.DBManager;
import sun.security.jca.GetInstance;

public class MyHibernate
{
	public static <T> T find(Class<T> clazz, int id)
	{
		ResultSet rs = null;
	    T returnedObject = null;
	    DBManager db = new DBManager("jdbc:hsqldb:C:\\java64\\hsqldb-2.3.4\\hsqldb\\testdb\\testDB;hsqldb.lock_file=false","sa","");
	    db.Connect();
		   
		try
		{
			// Armado de la query SQL
			String sqlQuery = SQLQuery(clazz, id);

			System.out.println(sqlQuery);

			// Ejecucion de la query
			rs = db.ExecuteQuery(sqlQuery);
			
			if(rs==null)
			{
				System.out.println("Resultado NULO");
			}
			if(rs.next())
			{
				// obtengo una instancia del DTO y le seteo los datos tomados del ResultSet
				returnedObject=GetInstance(clazz);
				
				InvokeSetters(returnedObject,rs,clazz);
				// si hay otra fila entonces hay inconsistencia de datos...
				if(rs.next())
				{
					throw new RuntimeException("Mas de una fila...");
				}
				return returnedObject;
			}
			return null;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
		finally
		{
			try
			{
				if( db!=null ) db.Close();
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
				throw new RuntimeException(ex);
			}
		}
	}

	public static <T> List<T> findAll(Class<T> clazz)
	{
		// PROGRAMAR AQUI
		return null;
	}

	public static Query createQuery(String hql)
	{
		// PROGRAMAR AQUI
		return null;
	}
	
	private static <T> String SQLQuery(Class<T> clazz, int id) 
	{
		// Armado de la query SQL
		String sqlQuery="";
		sqlQuery += "SELECT " + GetClassFields(clazz) + "\n";
		sqlQuery += "FROM " + GetTableName(clazz) + "\n";
		sqlQuery += SQLQueryJoins(clazz);
		sqlQuery += "WHERE " + IDColumnName(clazz) + " = " + id + "\n";
	
		return sqlQuery;
	}
	
	private static <T> String SQLQueryJoins(Class<T> dto)
	{
		Field[] fields = dto.getDeclaredFields();
		String tableName = dto.getAnnotation(Table.class).name();
		String columnNameId = "";
		String sqlQueryWithJoins = "";
		
		for (Field field : fields) {
			if (field.isAnnotationPresent(Id.class))
            	columnNameId = field.getAnnotation(Column.class).name();

            if (field.isAnnotationPresent(JoinColumn.class)) {
                String columnIdFK = field.getAnnotation(JoinColumn.class).name();
                Class<?> fieldType = field.getType();
                String tableFieldName = fieldType.getAnnotation(Table.class).name();
                
                sqlQueryWithJoins += "JOIN " + tableFieldName + " ON " + tableName + "." + columnIdFK + " = " + tableFieldName + "." + columnIdFK + "\n";
            }
        }

		return sqlQueryWithJoins;
	}

	private static <T> T GetInstance(Class<T> dtoClass)
	{
		try
		{
			return dtoClass.newInstance();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
	}

	private static String GetClassFields(Class dto)
	{

		Field[] fields=dto.getDeclaredFields();
		String fieldsConcat="";
		String fieldName="";

		for(int i=0; i<fields.length; i++)
		{
			if(fields[i].isAnnotationPresent(Column.class))
			{
				if(fields[i].getDeclaredAnnotation(Column.class).name()!="")
				{
					fieldName=fields[i].getDeclaredAnnotation(Column.class).name();
				}
				else
				{
					fieldName=fields[i].getName();
				}
			}else if(fields[i].isAnnotationPresent(JoinColumn.class)){
				if(fields[i].getDeclaredAnnotation(JoinColumn.class).name()!="")
				{
					fieldName=fields[i].getDeclaredAnnotation(JoinColumn.class).name();
				}
				else
				{
					fieldName=fields[i].getName();
				}
			}
			fieldsConcat+=fieldName+((i<fields.length-1)?", ":"");
		}

		return fieldsConcat;
	}

	private static <T> String GetTableName(Class<T> dto)
	{
		String tableName=dto.getAnnotation(Table.class).name();

		return tableName;
	}

	private static String IDColumnName(Class dto)
	{
		Field[] fields=dto.getDeclaredFields();
		String idColumnName="";

		for(Field field:fields)
		{
			if(field.isAnnotationPresent(Id.class)) idColumnName=field.getAnnotation(Column.class).name();
		}

		return idColumnName;
	}

	private static void InvokeSetters(Object dto, ResultSet rs, Class dtoClass)
	{
		Field[] fields=dtoClass.getDeclaredFields();
		Object valueColumn = null;
		String attName="";

		try
		{
			for(Field field:fields)
			{
				attName = field.getName();
				if(field.getAnnotation(Column.class)!=null)
				{
					valueColumn=rs.getObject(field.getAnnotation(Column.class).name());
				}
				else
				{
					if(field.getDeclaredAnnotation(JoinColumn.class)!=null)
					{
						valueColumn=rs.getObject(field.getAnnotation(JoinColumn.class).name());
					}
				}
				invoqueSetter(dto, attName, valueColumn);

				// Utilizar los setters para poner los valores a los respectivos
				// campos

			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
	}

	private static void invoqueSetter(Object dto, String attName, Object value)  {
		try{
		   // dado el attName obtengo el nombre del setter
			String mtdName=getSetterName(attName);
			Class[] argsType = new Class[1];
			Method mtd = null ;
			try{
			   // intento obtener el metodo...
			   argsType[0] = value.getClass();
			   mtd = dto.getClass().getMethod(mtdName,argsType);
		   }
		   catch(NoSuchMethodException ex){
			   // fallo... pruebo con el tipo primitivo
			   argsType[0] =_wrapperToType(value.getClass());
			   mtd = dto.getClass().getMethod(mtdName,argsType);
		   }
		   mtd.invoke(dto,value);

		   }
		   catch(Exception ex){
			   ex.printStackTrace();
			   throw new RuntimeException(ex);
		   }
	}

	private static Class _wrapperToType(Class clazz){
		if(clazz.equals(Byte.class)) return Byte.TYPE;
		if(clazz.equals(Short.class)) return Short.TYPE;
		if(clazz.equals(Integer.class)) return Integer.TYPE;
		if(clazz.equals(Long.class)) return Long.TYPE;
		if(clazz.equals(Character.class)) return Character.TYPE;
		if(clazz.equals(Float.class)) return Float.TYPE;
		if(clazz.equals(Double.class)) return Double.TYPE;
		return clazz;
	}

	private static String getSetterName(String attName)
	{
		System.out.println("atributo: "+ attName);
		String attNameUpperLetter = attName.substring(0, 1).toUpperCase() + attName.substring(1);
		return "set"+attNameUpperLetter;
		
	}

}
