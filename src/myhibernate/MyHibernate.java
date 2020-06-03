package myhibernate;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
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
	private static DBManager db;
	private static HashMap<String,String> joinHm;
	

	public static <T> T find(Class<T> clazz, int id)
	{
		ResultSet rs = null;
	    T returnedObject = null;
	    db = new DBManager();
	    db.Connect();
		   
		try
		{
			// Armado de la query SQL
			String sqlQuery = SQLQueryWithId(clazz, id);
//			System.out.println(sqlQuery);

			// Ejecucion de la query
			rs = db.ExecuteQuery(sqlQuery);
			
			if(rs == null) System.out.println("Resultado NULO");
			if(rs.next())
			{
				// obtengo una instancia del DTO y le seteo los datos tomados del ResultSet
				returnedObject = GetInstance(clazz);				
				InvokeSetters(returnedObject,rs,clazz,returnedObject);
				
				// si hay otra fila entonces hay inconsistencia de datos...
				if(rs.next()) throw new RuntimeException("Mas de una fila...");
				
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
				if(db != null) db.Close();
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
		ResultSet rs = null;
	    List<T> listReturned = new ArrayList<T>();
	    T returnedObject = null;
	    db = new DBManager();
	    db.Connect();
		   
		try
		{
			// Armado de la query SQL
			String sqlQuery = SQLQuery(clazz);
//			System.out.println(sqlQuery);

			// Ejecucion de la query
			rs = db.ExecuteQuery(sqlQuery);
			
			if(rs == null) System.out.println("Tabla Vacia");
			do{
				returnedObject = null;
				if(rs.next())
				{
					returnedObject = GetInstance(clazz);
					InvokeSetters(returnedObject,rs,clazz,returnedObject);
					if (returnedObject != null) listReturned.add(returnedObject);
				}
	        } while (returnedObject != null);
			return listReturned;
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
				if(db != null) db.Close();
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
				throw new RuntimeException(ex);
			}
		}
	}

	public static Query createQuery(String hql)
	{
		// PROGRAMAR AQUI
		return null;
	}
	
	private static <T> String SQLQuery(Class<T> clazz) 
	{
		// Armado de la query SQL
		String sqlQuery="";
		String alias = "a0";
		String select = GetClassFields(clazz,alias) + SQLQueryJoinFields(clazz);
		String joins = SQLQueryJoins(clazz);
		sqlQuery += "SELECT " + select + "\n";
		sqlQuery += "FROM " + GetTableName(clazz) + " " + alias + "\n";
		sqlQuery += joins;
	
		return sqlQuery;
	}
	
	private static <T> String SQLQueryWithId(Class<T> clazz, int id) 
	{
		// Armado de la query SQL
		if (joinHm == null)
			joinHm = new HashMap<String,String>();
		String sqlQuery="";
		String alias = "a0";
		sqlQuery += SQLQuery(clazz);
		sqlQuery += "WHERE " + alias + "." + IDColumnName(clazz) + " = " + id + "\n";
	
		return sqlQuery;
	}
	
	
	private static <T> String SQLQueryJoinFields(Class<T> dto)
	{
		int counter2 = 0;
		int counter = 1;
		Field[] fields = dto.getDeclaredFields();
		String tableName = dto.getAnnotation(Table.class).name();
		String columnNameId = "";
		String joinFields = "";
		String fieldName = "";
		
		for (Field field : fields) 
		{
            if (field.isAnnotationPresent(JoinColumn.class)) 
            {
            	if (counter2 != counter)
            	{
            		joinFields = joinFields + ", ";
            		counter2++;
            	}
                String columnIdFK = field.getAnnotation(JoinColumn.class).name();
            	Class<?> fieldType = field.getType();
                String tableFieldName = fieldType.getAnnotation(Table.class).name();
                
                Field[] fieldsJoin = fieldType.getDeclaredFields();
                for (int i=0; i<fieldsJoin.length; i++)
                {
                	if(fieldsJoin[i].isAnnotationPresent(Column.class))
        			{
        				if(fieldsJoin[i].getDeclaredAnnotation(Column.class).name()!="")
        					fieldName=fieldsJoin[i].getDeclaredAnnotation(Column.class).name();
        				else
        					fieldName=fieldsJoin[i].getName();
        			}
        			else if(fieldsJoin[i].isAnnotationPresent(JoinColumn.class))
        			{
        				if(fieldsJoin[i].getDeclaredAnnotation(JoinColumn.class).name()!="")
        					fieldName=fieldsJoin[i].getDeclaredAnnotation(JoinColumn.class).name();
        				else
        					fieldName=fieldsJoin[i].getName();
        			}
        			joinFields += "a" + counter + "." + fieldName + " as " + "a" + counter + fieldName + ((i<fieldsJoin.length-1)?", ":"");
                }
         
                counter++;
            }
		} 
		
		return joinFields;
	}
	
	private static <T> String SQLQueryJoins(Class<T> dto)
	{
		int counter = 1;
		Field[] fields = dto.getDeclaredFields();
		String tableName = dto.getAnnotation(Table.class).name();
		String columnNameId = "";
		String sqlQueryWithJoins = "";
		
		for (Field field : fields) 
		{
            if (field.isAnnotationPresent(JoinColumn.class)) 
            {
                String columnIdFK = field.getAnnotation(JoinColumn.class).name();
            	Class<?> fieldType = field.getType();
                String tableFieldName = fieldType.getAnnotation(Table.class).name();
                
                Field[] fieldsJoin = fieldType.getDeclaredFields();
                for (Field f : fieldsJoin)
                {
                	if (f.isAnnotationPresent(Id.class))
                		columnNameId = f.getAnnotation(Column.class).name();
                }

                joinHm.put(tableFieldName,"a" + counter);
                sqlQueryWithJoins += "LEFT JOIN " + tableFieldName + " a" + counter + " ON " + "a0." + columnIdFK + " = " + "a" + counter + "." + columnNameId + "\n";
                counter++;
            }
		} 
		
		return sqlQueryWithJoins;
	}

	private static <T> T GetInstance(Class<?> dtoClass)
	{
		try
		{
			return (T)dtoClass.newInstance();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
	}

	private static String GetClassFields(Class dto, String alias)
	{
		Field[] fields=dto.getDeclaredFields();
		String fieldsConcat="";
		String fieldName="";

		for(int i=0; i<fields.length; i++)
		{
			if(fields[i].isAnnotationPresent(Column.class))
			{
				if(fields[i].getDeclaredAnnotation(Column.class).name()!="")
					fieldName=fields[i].getDeclaredAnnotation(Column.class).name();
				else
					fieldName=fields[i].getName();
			}
			else if(fields[i].isAnnotationPresent(JoinColumn.class))
			{
				if(fields[i].getDeclaredAnnotation(JoinColumn.class).name()!="")
					fieldName=fields[i].getDeclaredAnnotation(JoinColumn.class).name();
				else
					fieldName=fields[i].getName();
			}
			
			fieldsConcat += alias + "." + fieldName + " as " + alias + fieldName + ((i<fields.length-1)?", ":"");
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

	private static <T> void InvokeSetters(Object dto, ResultSet rs, Class dtoClass, T returnedObject)
	{
		Field[] fields = dtoClass.getDeclaredFields();
		Object valueColumn = null;
		Class<?> columnType;
		String attName = "";

		try
		{
			for(Field field:fields)
			{
				// Utilizar los setters para poner los valores a las respectivas propiedades
				attName = field.getName();
				columnType = field.getType();
				
				if(field.getAnnotation(Column.class) != null)
				{

					valueColumn = rs.getObject("a0" + field.getAnnotation(Column.class).name());
					SettersPrimitiveTypes(dto, attName, valueColumn, columnType);
				}
				else
				{
					if(field.getDeclaredAnnotation(JoinColumn.class) != null)
					{ 
						String joinAlias = joinHm.get(columnType.getAnnotation(Table.class).name());
						valueColumn = rs.getObject(joinAlias + field.getAnnotation(JoinColumn.class).name());
						SettersEntities(dto, attName, valueColumn, rs, field, returnedObject);
					}
				}
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
	}

	private static void SettersPrimitiveTypes(Object dto, String attName, Object value, Class<?> columnType)
	{
		try
		{
		   // dado el attName obtengo el nombre del setter
		   String mtdName = getSetterName(attName);
		   Class[] argsType = new Class[1];
		   Method[] mtds = dto.getClass().getDeclaredMethods();
		   Method mtd = null ;
		   
		   	try
		   	{
			   	// intento obtener el metodo...
			   	argsType[0] = value.getClass();
			   	mtd = dto.getClass().getMethod(mtdName,argsType);
		   	}
		   	catch(NoSuchMethodException ex)
		   	{
			   	// fallo... pruebo con el tipo primitivo
			   	argsType[0] =_wrapperToType(columnType);
			   	mtd = dto.getClass().getMethod(mtdName,argsType);
			   	
			   	if(columnType.equals(boolean.class))
			   	{
			   		if ((int)value == 0) 
				   		value = false;
				   	else
				   		value = true;	
			   	}		   		
		   	}
		   
		   	mtd.invoke(dto,value);
	    }
	    catch(Exception ex)
		{
	    	ex.printStackTrace();
	    	throw new RuntimeException(ex);
	    }
	}
	
	private static <T> void SettersEntities(Object dto, String attName, Object value, ResultSet rs, Field field, T returnedObject)
	{
		Object objectEntity = null;
		String mtdName = getSetterName(attName);
	    Class[] argsType = new Class[1];
		Method mtd = null ;
		
        try 
        {
        	T entityResult = EntityInstance(value, field, returnedObject);
        	
        	// instanciar un objeto nuevo y llenar todos los campos
        	mtdName = getSetterName(attName);
        	argsType[0] =_wrapperToType(field.getType().newInstance().getClass());
        	mtd = dto.getClass().getMethod(mtdName,argsType);
        	
        	mtd.invoke(dto,entityResult);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }
	}
	
	private static <T> T EntityInstance(Object value, Field field, T returnedObject)
	{
		ResultSet rs;
		
		try
    	{
			if (value != null) 
			{
				Class<?> entityClass = field.getType().newInstance().getClass();
	        	String sqlQuery = SQLQueryWithId(entityClass, (Integer)value);
		
//				System.out.println(sqlQuery);
		
				// Ejecucion de la query
				rs = db.ExecuteQuery(sqlQuery);
					
				if(rs==null)
				{
					System.out.println("Resultado NULO");
				}
				if(rs.next())
				{
					// obtengo una instancia del DTO y le seteo los datos tomados del ResultSet
					returnedObject = GetInstance(entityClass);
					
					InvokeSetters(returnedObject, rs, entityClass, returnedObject);
					// si hay otra fila entonces hay inconsistencia de datos...
					if(rs.next())
					{
						throw new RuntimeException("Mas de una fila...");
					}
					
					return returnedObject;
				}				
			}    		
    	}
    	catch (Exception ex) 
    	{
    		ex.printStackTrace();
    	}
		
		return null;
	}

	private static Class _wrapperToType(Class clazz)
	{
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
		//System.out.println("atributo: " + attName);
		String attNameUpperLetter = attName.substring(0, 1).toUpperCase() + attName.substring(1);
		return "set" + attNameUpperLetter;		
	}

}
