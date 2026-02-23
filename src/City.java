public class City {
    public String Name;
    public double Latitude;
    public double Longitude;

    public City(String name, double latitude, double longitude){
        this.Name = name;
        this.Latitude = latitude;
        this.Longitude = longitude;
    }

    public String getName() { return Name; };
    public double getLat() { return Latitude; };
    public double getLng() { return Longitude; };

}
