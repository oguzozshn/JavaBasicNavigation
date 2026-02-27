public class City {
    public String Name;
    public String Latitude;
    public String Longitude;

    public City(String name, String latitude, String longitude){
        this.Name = name;
        this.Latitude = latitude;
        this.Longitude = longitude;
    }

    public String getName() { return Name; };
    public String getLat() { return Latitude; };
    public String getLng() { return Longitude; }

    public double lat() { return Double.parseDouble(Latitude); }
    public double lon() { return Double.parseDouble(Longitude); }
}
