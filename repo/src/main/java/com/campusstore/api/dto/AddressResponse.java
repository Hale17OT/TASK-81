package com.campusstore.api.dto;

/**
 * Owner-scoped address response. Address fields are decrypted server-side
 * for the authenticated owner only.
 */
public class AddressResponse {

    private Long id;
    private String label;
    private String street;
    private String city;
    private String state;
    private String zipCode;
    private Boolean isPrimary;

    public AddressResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
}
