/* See LICENSE for licensing and NOTICE for copyright. */
package org.passay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains password related information used by rules to perform password validation.
 *
 * @author  Middleware Services
 */
public class PasswordData
{

  /** Enum to define the origin of a password. */
  public enum Origin {

    /** a password which was generated by a typical human user. */
    User,

    /** a password which was generated by a sufficient random source. */
    Generated
  }

  /** Stores the password. */
  private String password;

  /** Stores the username. */
  private String username;

  /** Password references. */
  private List<Reference> passwordReferences = new ArrayList<>();

  /** Origin of this password. */
  private Origin origin = Origin.User;


  /** Default constructor. */
  public PasswordData() {}


  /**
   * Creates a new password data. The origin of this data is assumed to be {@link Origin#User} by default.
   *
   * @param  p  password
   */
  public PasswordData(final String p)
  {
    setPassword(p);
  }


  /**
   * Creates a new password data. The origin of this data is assumed to be {@link Origin#User} by default.
   *
   * @param  u  username
   * @param  p  password
   */
  public PasswordData(final String u, final String p)
  {
    setUsername(u);
    setPassword(p);
  }


  /**
   * Creates a new password data.
   *
   * @param  p  password
   * @param  o  origin
   */
  public PasswordData(final String p, final Origin o)
  {
    setPassword(p);
    setOrigin(o);
  }


  /**
   * Creates a new password data.
   *
   * @param  u  username
   * @param  p  password
   * @param  o  origin
   */
  public PasswordData(final String u, final String p, final Origin o)
  {
    setUsername(u);
    setPassword(p);
    setOrigin(o);
  }


  /**
   * Sets the password.
   *
   * @param  p  password
   */
  public void setPassword(final String p)
  {
    if (p == null) {
      throw new NullPointerException("Password cannot be null");
    }
    password = p;
  }


  /**
   * Returns the password.
   *
   * @return  password
   */
  public String getPassword()
  {
    return password;
  }


  /**
   * Sets the origin.
   *
   * @param  o  origin
   */
  public void setOrigin(final Origin o)
  {
    if (o == null) {
      throw new NullPointerException("Origin cannot be null");
    }
    origin = o;
  }


  /**
   * Returns the origin.
   *
   * @return  origin
   */
  public Origin getOrigin()
  {
    return origin;
  }


  /**
   * Sets the username.
   *
   * @param  s  username
   */
  public void setUsername(final String s)
  {
    if (s == null) {
      throw new NullPointerException("Username cannot be null");
    }
    username = s;
  }


  /**
   * Returns the username.
   *
   * @return  username
   */
  public String getUsername()
  {
    return username;
  }


  /**
   * Returns the password references.
   *
   * @return  password references
   */
  public List<Reference> getPasswordReferences()
  {
    return passwordReferences;
  }


  /**
   * Returns the password references that match the supplied reference type.
   *
   * @param  <T>  type of password reference
   * @param  type  of reference to match
   *
   * @return  unmodifiable list of password references
   */
  @SuppressWarnings("unchecked")
  public <T extends Reference> List<T> getPasswordReferences(final Class<T> type)
  {
    final List<T> l = new ArrayList<>();
    if (passwordReferences != null) {
      l.addAll(passwordReferences.stream().filter(
        type::isInstance).map(r -> (T) r).collect(Collectors.toList()));
    }
    return Collections.unmodifiableList(l);
  }


  /**
   * Sets the password references.
   *
   * @param  l  password references
   */
  public void setPasswordReferences(final List<Reference> l)
  {
    passwordReferences = l;
  }


  /**
   * Convenience method for creating user password data with all of its properties. Properties are ignored if they are
   * null.
   *
   * @param  p  password
   * @param  u  username
   * @param  r  references
   *
   * @return  password data
   */
  public static PasswordData newInstance(final String p, final String u, final List<Reference> r)
  {
    return newInstance(p, u, Origin.User, r);
  }


  /**
   * Convenience method for creating a password data with all of its properties. Properties are ignored if they are
   * null.
   *
   * @param  p  password
   * @param  u  username
   * @param  o  origin
   * @param  r  references
   *
   * @return  password data
   */
  public static PasswordData newInstance(final String p, final String u, final Origin o, final List<Reference> r)
  {
    final PasswordData pd = new PasswordData();
    if (p != null) {
      pd.setPassword(p);
    }
    if (u != null) {
      pd.setUsername(u);
    }
    if (o != null) {
      pd.setOrigin(o);
    }
    if (r != null) {
      pd.setPasswordReferences(r);
    }
    return pd;
  }


  @Override
  public String toString()
  {
    return
      String.format(
        "%s@%h::username=%s,password=%s,origin=%s,passwordReferences=%s",
        getClass().getName(),
        hashCode(),
        username,
        password,
        origin,
        passwordReferences);
  }


  /** Reference to another password. */
  public interface Reference
  {


    /**
     * Returns the password associated with this reference.
     *
     * @return  password string
     */
    String getPassword();
  }


  /** Reference to an historical password. */
  public static class HistoricalReference extends AbstractReference
  {


    /**
     * Creates a new historical reference.
     *
     * @param  pass  password string
     */
    public HistoricalReference(final String pass)
    {
      super(null, pass);
    }


    /**
     * Creates a new historical reference.
     *
     * @param  lbl  label for this password
     * @param  pass  password string
     */
    public HistoricalReference(final String lbl, final String pass)
    {
      super(lbl, pass);
    }
  }


  /** Reference to a source password. */
  public static class SourceReference extends AbstractReference
  {


    /**
     * Creates a new source reference.
     *
     * @param  pass  password string
     */
    public SourceReference(final String pass)
    {
      super(null, pass);
    }


    /**
     * Creates a new source reference.
     *
     * @param  lbl  label for this password
     * @param  pass  password string
     */
    public SourceReference(final String lbl, final String pass)
    {
      super(lbl, pass);
    }
  }


  /** Common password reference implementation. */
  public abstract static class AbstractReference implements Reference
  {

    /** Label to identify this password. */
    private final String label;

    /** Reference password. */
    private final String password;


    /**
     * Creates a new abstract reference.
     *
     * @param  lbl  label for this password
     * @param  pass  password string
     */
    public AbstractReference(final String lbl, final String pass)
    {
      label = lbl;
      password = pass;
    }


    /**
     * Returns the label.
     *
     * @return  reference label
     */
    public String getLabel()
    {
      return label;
    }


    @Override
    public String getPassword()
    {
      return password;
    }


    @Override
    public String toString()
    {
      return String.format("%s@%h::label=%s,password=%s", getClass().getName(), hashCode(), label, password);
    }
  }
}
