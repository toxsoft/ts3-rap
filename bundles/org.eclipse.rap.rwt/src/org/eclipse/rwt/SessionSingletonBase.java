/*******************************************************************************
 * Copyright (c) 2002-2007 Innoopract Informationssysteme GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Innoopract Informationssysteme GmbH - initial API and implementation
 ******************************************************************************/
package org.eclipse.rwt;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.rwt.internal.service.ContextProvider;
import org.eclipse.rwt.internal.service.IServiceStateInfo;
import org.eclipse.rwt.service.ISessionStore;



/**
 * <p>Subclasses of <code>SessionSingletonBase</code> provide access to a 
 * unique instance of their type with session scope. This means that in the 
 * context of one user session <code>getInstance(Class)</code> will always return 
 * the same object, but for different user sessions the returned instances 
 * will be different.</p>
 * 
 * <p>usage:
 * <pre>
 * public class FooSingleton extends SessionSingletonBase {
 *  
 *   private FooSingleton() {}
 * 
 *   public static FooSingleton getInstance() {
 *     return ( FooSingleton )getInstance( FooSingleton.class );
 *   }
 * }
 * </pre>
 * </p>
 * 
 * @since 1.0
 */
public abstract class SessionSingletonBase {

  /**
  * <b>IMPORTANT:</b> This constant is <em>not</em> part of the RWT
  * public API. It is marked public only so that it can be shared
  * within the packages provided by RWT. It should never be
  * referenced from application code.
  */
  public static final String LOCK
    = SessionSingletonBase.class.getName() + ".Lock";
  
  /**
   * This is used as prefix for the key under which the instance
   * is stored as session attribute. The key consists of the prefix
   * and the fully qualified classname of the singleton type. 
   */
  private final static String PREFIX = "com_w4t_session_singleton_";
  private static final String LOCK_POSTFIX = "#typeLock";
  
  private final static Class[] EMPTY_PARAMS = new Class[ 0 ];
  
  private final static Map instanceKeyMap = new Hashtable();
  private final static Map lockKeyMap = new Hashtable();

  
  /** 
   * Returns the singleton instance of the specified type that is stored
   * in the current session context. If no instance exists yet, a new
   * one will be created. Therefore the specified type should have
   * an parameterless default constructor.
   * 
   * @param type specifies the session singleton instance type.
   * @return the unique instance of the specified type that is associated
   *         with the current user session context.  
   */
  protected static Object getInstance( final Class type ) {
    // Note [fappel]: Since this code is performance critical, don't change
    //                anything without checking it against a profiler.
    IServiceStateInfo stateInfo = ContextProvider.getStateInfo();
    Object result = null;
    if( stateInfo != null ) {
      result = stateInfo.getAttribute( getInstanceKey( type ) );
    }
    if( result == null ) {
      synchronized( getInstanceLock( type ) ) {
        result = getInstanceInternal( type );
      }
      if( stateInfo != null ) {
        stateInfo.setAttribute( getInstanceKey( type ), result );
      }
    }
    return result;
  }
  

  //////////////////
  // helping methods
  
  private static Object getInstanceLock( final Class type ) {
    // create a lock per session instance to avoid deadlocks
    ISessionStore session = ContextProvider.getSession();
    Object result = null;
    synchronized( session.getAttribute( LOCK ) ) {
      result = session.getAttribute( getLockKey( type ) );
      if( result == null ) {
        result = new Object();
        session.setAttribute( getLockKey( type ), result );
      }
    }
    return result;
  }
  
  private static String getInstanceKey( final Class type ) {
    // Note [fappel]: Since this code is performance critical, don't change
    //                anything without checking it against a profiler.
    String name = type.getName();
    String result = ( String )instanceKeyMap.get( name );
    if( result == null ) {
      StringBuffer key = new StringBuffer( PREFIX );
      key.append( name );
      instanceKeyMap.put( name, key.toString() );
    }
    return result;
  }
  
  private static String getLockKey( final Class type ) {
    // Note [fappel]: Since this code is performance critical, don't change
    //                anything without checking it against a profiler.
    String name = type.getName();
    String result = ( String )lockKeyMap.get( name );
    if( result == null ) {
      StringBuffer key = new StringBuffer( PREFIX );
      key.append( name );
      key.append( LOCK_POSTFIX );
      lockKeyMap.put( name, key.toString() );
    }
    return result;
  }
  
  private static Object getInstanceInternal( final Class type ) {
    Object result = getAttribute( getInstanceKey( type ) ); 
    if( result == null ) {
      try {
        Constructor constructor = type.getDeclaredConstructor( EMPTY_PARAMS );
        if( constructor.isAccessible() ) {
          result = type.newInstance();
        } else {
          constructor.setAccessible( true );
          result = constructor.newInstance( null );
        }
      } catch( final SecurityException ex ) {
        String msg =   "Could not created the session singleton instance of '"
                     + type.getName() 
                     + "' due to security restrictions that probably do "
                     + "not allow reflection.";
        throw new RuntimeException( msg, ex );
      } catch( final IllegalArgumentException iae ) {
        String msg =   "Could not create the session singleton instance of '"
                     + type.getName() 
                     + "'. Probably there is no parameterless constructor.";
        throw new RuntimeException( msg, iae );
      } catch( final NoSuchMethodException nsme ) {
        String msg =   "Could not create the session singleton instance of '"
                     + type.getName() 
                     + "'. Probably there is no parameterless constructor.";
        throw new RuntimeException( msg, nsme );
      } catch( final InstantiationException ise ) {
        String msg =   "Could not create the session singleton instance of '"
                     + type.getName() 
                     + "'. Unable to create an instance.";
        throw new RuntimeException( msg, ise );
      } catch( final IllegalAccessException iae ) {
        String msg =   "Could not create the session singleton instance of '"
                      + type.getName() 
                      + "'. Not allowed to access the constructor "
                      + "for unknown reasons.";
        throw new RuntimeException( msg, iae );
      } catch( final InvocationTargetException ite ) {
        String msg =   "Could not create the session singleton instance of '"
                     + type.getName() 
                     + "' because an Exception was thrown by the constructor:\n"
                     + ite.getCause().getMessage()
                     + ".";
        throw new RuntimeException( msg, ite );
      }
      setAttribute( getInstanceKey( type ), result );
    }
    return result;
  }
  
  private static Object getAttribute( final String name ) {
    return ContextProvider.getSession().getAttribute( name );
  }

  private static void setAttribute( final String name, final Object object ) {
    ContextProvider.getSession().setAttribute( name, object );
  }

}
