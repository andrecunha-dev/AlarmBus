import SwiftUI
import MapKit
import CoreLocation
import AVFoundation
import AudioToolbox

class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    @Published var currentLocation: CLLocationCoordinate2D?
    @Published var distanceToDest: Double?

    var destinationLocation: CLLocationCoordinate2D?
    var alertDistanceMeters: Double = 500
    var isAlarmActive = false
    var onTriggerAlarm: (() -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        DispatchQueue.main.async {
            self.currentLocation = loc.coordinate

            if let dest = self.destinationLocation, self.isAlarmActive {
                let destLoc = CLLocation(latitude: dest.latitude, longitude: dest.longitude)
                let dist = loc.distance(from: destLoc)
                self.distanceToDest = dist

                if dist <= self.alertDistanceMeters {
                    self.onTriggerAlarm?()
                }
            }
        }
    }
}

struct ContentView: View {
    @StateObject private var locationManager = LocationManager()

    @State private var searchText = ""
    @State private var selectedAddress = ""
    @State private var selectedDistance: Double = 500
    @State private var isVibrationActive = true
    @State private var volumeLevel: Double = 100
    @State private var isAlarmActive = false
    @State private var showMapPicker = false
    @State private var destinationCoordinate: CLLocationCoordinate2D? = nil
    @State private var historico: [String] = []

    @State private var audioPlayer: AVAudioPlayer?
    @State private var isRinging = false

    let purpleColor = Color(red: 103/255, green: 80/255, blue: 164/255)

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 12) {
                    // Banner Durma Tranquilo
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(LinearGradient(gradient: Gradient(colors: [purpleColor, Color.purple]), startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(height: 82)

                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Durma Tranquilo")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                Text("Eu aviso quando estiver chegando.")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.white.opacity(0.9))
                            }
                            Spacer()
                            Image(systemName: "moon.stars.fill")
                                .font(.system(size: 32))
                                .foregroundColor(.white.opacity(0.8))
                        }
                        .padding(.horizontal, 16)
                    }

                    // Campo de Busca
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(purpleColor)

                        TextField("Digite ou escolha o destino", text: $searchText, onCommit: {
                            buscarEndereco(searchText)
                        })
                        .font(.system(size: 13))

                        if !searchText.isEmpty {
                            Button(action: {
                                searchText = ""
                                destinationCoordinate = nil
                                selectedAddress = ""
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.gray)
                            }
                        }

                        Button(action: {
                            buscarEndereco(searchText)
                        }) {
                            Image(systemName: "chevron.right.circle.fill")
                                .font(.system(size: 20))
                                .foregroundColor(purpleColor)
                        }
                    }
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(10)
                    .shadow(color: .black.opacity(0.05), radius: 2)

                    // Botão Escolher no Google Maps
                    Button(action: { showMapPicker = true }) {
                        HStack {
                            Image(systemName: "map.fill")
                            Text("Escolher no Google Maps")
                                .font(.system(size: 13, weight: .medium))
                        }
                        .foregroundColor(purpleColor)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(purpleColor.opacity(0.1))
                        .cornerRadius(8)
                    }

                    // Histórico
                    if !historico.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Últimas localizações")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(purpleColor)

                            VStack(spacing: 0) {
                                ForEach(historico, id: \.self) { item in
                                    Button(action: {
                                        searchText = item
                                        buscarEndereco(item)
                                    }) {
                                        HStack {
                                            Image(systemName: "clock.arrow.circlepath")
                                                .foregroundColor(.gray)
                                            Text(item)
                                                .font(.system(size: 12))
                                                .foregroundColor(.primary)
                                                .lineLimit(1)
                                            Spacer()
                                            Image(systemName: "chevron.right")
                                                .font(.system(size: 12))
                                                .foregroundColor(.gray.opacity(0.5))
                                        }
                                        .padding(12)
                                    }
                                    Divider()
                                }
                            }
                            .background(Color.white)
                            .cornerRadius(12)
                        }
                    }

                    // Mapa do iOS (MapKit)
                    ZStack(alignment: .bottomTrailing) {
                        MapViewRepresentable(
                            currentLocation: locationManager.currentLocation,
                            destinationCoordinate: destinationCoordinate,
                            radiusMeters: selectedDistance
                        )
                        .frame(height: 250)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.2), lineWidth: 1))

                        Text("Alarme em \(Int(selectedDistance >= 1000 ? selectedDistance/1000 : selectedDistance)) \(selectedDistance >= 1000 ? "km" : "m")")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(purpleColor)
                            .cornerRadius(14)
                            .padding(8)
                    }

                    // Seleção de Distância
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Quando devo avisar?")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(purpleColor)

                        HStack(spacing: 8) {
                            ForEach([300.0, 500.0, 750.0, 1000.0], id: \.self) { dist in
                                Button(action: { selectedDistance = dist }) {
                                    Text(dist >= 1000 ? "1 km" : "\(Int(dist)) m")
                                        .font(.system(size: 12, weight: selectedDistance == dist ? .bold : .medium))
                                        .frame(maxWidth: .infinity, minHeight: 42)
                                        .background(selectedDistance == dist ? purpleColor : Color.white)
                                        .foregroundColor(selectedDistance == dist ? .white : .primary)
                                        .cornerRadius(10)
                                }
                            }
                        }
                    }

                    // Configurações de Som e Volume
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Como deseja ser avisado?")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(purpleColor)

                        VStack(spacing: 0) {
                            HStack {
                                Image(systemName: "speaker.wave.2.fill")
                                    .foregroundColor(purpleColor)
                                VStack(alignment: .leading) {
                                    Text("Som do Alarme")
                                        .font(.system(size: 13, weight: .bold))
                                    Text("Toque de alarme padrão")
                                        .font(.system(size: 11))
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                            }
                            .padding(12)

                            Divider()

                            Toggle(isOn: $isVibrationActive) {
                                HStack {
                                    Image(systemName: "iphone.radiowaves.left.and.right")
                                        .foregroundColor(purpleColor)
                                    Text("Vibração")
                                        .font(.system(size: 13, weight: .bold))
                                }
                            }
                            .padding(12)

                            Divider()

                            VStack(alignment: .leading) {
                                HStack {
                                    Text("Nível do Volume do Alarme")
                                        .font(.system(size: 13, weight: .bold))
                                    Spacer()
                                    Text("\(Int(volumeLevel))%")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(purpleColor)
                                }
                                Slider(value: $volumeLevel, in: 0...100, step: 10)
                            }
                            .padding(12)
                        }
                        .background(Color.white)
                        .cornerRadius(12)
                    }

                    // Botão Ativar / Desativar Alerta
                    Button(action: {
                        if destinationCoordinate == nil {
                            return
                        }
                        isAlarmActive.toggle()
                        locationManager.isAlarmActive = isAlarmActive
                        locationManager.destinationLocation = destinationCoordinate
                        locationManager.alertDistanceMeters = selectedDistance

                        if !isAlarmActive {
                            stopAlarm()
                        }
                    }) {
                        HStack {
                            Image(systemName: isAlarmActive ? "bell.badge.fill" : "bell.fill")
                            Text(isAlarmActive ? "Desativar alerta" : "Ativar alerta")
                                .font(.system(size: 16, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(isAlarmActive ? Color.red : purpleColor)
                        .cornerRadius(12)
                    }

                    if isRinging {
                        Button(action: stopAlarm) {
                            Text("🚨 DESATIVAR ALARME AGORA")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity, minHeight: 52)
                                .background(Color.red)
                                .cornerRadius(12)
                        }
                    }

                    Text("Mantenha o GPS ligado e permita o funcionamento em segundo plano.")
                        .font(.system(size: 11))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(red: 248/255, green: 246/255, blue: 250/255))
            .navigationTitle("Alerta de Destino")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showMapPicker) {
                MapPickerView(
                    currentCoord: destinationCoordinate ?? locationManager.currentLocation ?? CLLocationCoordinate2D(latitude: -23.55052, longitude: -46.633308),
                    onSelect: { coord, address in
                        destinationCoordinate = coord
                        selectedAddress = address
                        searchText = address
                        salvarNoHistorico(address)
                        showMapPicker = false
                    }
                )
            }
            .onAppear {
                locationManager.onTriggerAlarm = {
                    triggerAlarm()
                }
            }
        }
    }

    private func buscarEndereco(_ query: String) {
        guard !query.isEmpty else { return }
        let geocoder = CLGeocoder()
        geocoder.geocodeAddressString(query) { placemarks, error in
            if let place = placemarks?.first, let location = place.location {
                DispatchQueue.main.async {
                    self.destinationCoordinate = location.coordinate
                    self.selectedAddress = query
                    self.salvarNoHistorico(query)
                }
            }
        }
    }

    private func salvarNoHistorico(_ item: String) {
        if !historico.contains(item) {
            historico.insert(item, at: 0)
            if historico.count > 3 { historico.removeLast() }
        }
    }

    private func triggerAlarm() {
        isRinging = true
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        if let soundURL = Bundle.main.url(forResource: "alarm", withExtension: "mp3") {
            try? audioPlayer = AVAudioPlayer(contentsOf: soundURL)
            audioPlayer?.numberOfLoops = -1
            audioPlayer?.volume = Float(volumeLevel / 100.0)
            audioPlayer?.play()
        }
    }

    private func stopAlarm() {
        isRinging = false
        audioPlayer?.stop()
        isAlarmActive = false
        locationManager.isAlarmActive = false
    }
}

// MapView do iOS
struct MapViewRepresentable: UIViewRepresentable {
    var currentLocation: CLLocationCoordinate2D?
    var destinationCoordinate: CLLocationCoordinate2D?
    var radiusMeters: Double

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.showsUserLocation = true
        map.delegate = context.coordinator
        return map
    }

    func updateUIView(_ uiView: MKMapView, context: Context) {
        uiView.removeOverlays(uiView.overlays)
        uiView.removeAnnotations(uiView.annotations)

        if let dest = destinationCoordinate {
            let anno = MKPointAnnotation()
            anno.coordinate = dest
            anno.title = "Destino"
            uiView.addAnnotation(anno)

            let circle = MKCircle(center: dest, radius: radiusMeters)
            uiView.addOverlay(circle)

            let region = MKCoordinateRegion(center: dest, latitudinalMeters: radiusMeters * 3, longitudinalMeters: radiusMeters * 3)
            uiView.setRegion(region, animated: true)
        } else if let current = currentLocation {
            let region = MKCoordinateRegion(center: current, latitudinalMeters: 2000, longitudinalMeters: 2000)
            uiView.setRegion(region, animated: true)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator: NSObject, MKMapViewDelegate {
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let circleOverlay = overlay as? MKCircle {
                let renderer = MKCircleRenderer(circle: circleOverlay)
                renderer.fillColor = UIColor(red: 103/255, green: 80/255, blue: 164/255, alpha: 0.2)
                renderer.strokeColor = UIColor(red: 103/255, green: 80/255, blue: 164/255, alpha: 0.8)
                renderer.lineWidth = 2
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}

// Seletor em Tela Cheia do iOS
struct MapPickerView: View {
    @Environment(\.presentationMode) var presentationMode
    @State var currentCoord: CLLocationCoordinate2D
    @State var selectedAddress = "Carregando endereço..."
    var onSelect: (CLLocationCoordinate2D, String) -> Void

    var body: some View {
        VStack {
            HStack {
                Button("Fechar") { presentationMode.wrappedValue.dismiss() }
                Spacer()
                Text("Escolher Destino no Mapa")
                    .bold()
                Spacer()
            }
            .padding()

            MapPickerRepresentable(coordinate: $currentCoord, onTap: { coord in
                currentCoord = coord
                reverseGeocode(coord)
            })

            VStack(alignment: .leading, spacing: 10) {
                Text("Endereço do Ponto:")
                    .font(.caption)
                    .foregroundColor(.gray)
                Text(selectedAddress)
                    .font(.headline)

                Button(action: {
                    onSelect(currentCoord, selectedAddress)
                }) {
                    Text("CONFIRMAR ESTE DESTINO")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(Color(red: 103/255, green: 80/255, blue: 164/255))
                        .cornerRadius(12)
                }
            }
            .padding()
            .background(Color.white)
        }
        .onAppear {
            reverseGeocode(currentCoord)
        }
    }

    private func reverseGeocode(_ coord: CLLocationCoordinate2D) {
        let geocoder = CLGeocoder()
        let loc = CLLocation(latitude: coord.latitude, longitude: coord.longitude)
        geocoder.reverseGeocodeLocation(loc) { placemarks, _ in
            if let p = placemarks?.first {
                DispatchQueue.main.async {
                    self.selectedAddress = "\(p.thoroughfare ?? ""), \(p.subThoroughfare ?? "") - \(p.locality ?? "")"
                }
            }
        }
    }
}

struct MapPickerRepresentable: UIViewRepresentable {
    @Binding var coordinate: CLLocationCoordinate2D
    var onTap: (CLLocationCoordinate2D) -> Void

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        let gesture = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.mapTapped(_:)))
        map.addGestureRecognizer(gesture)
        return map
    }

    func updateUIView(_ uiView: MKMapView, context: Context) {
        uiView.removeAnnotations(uiView.annotations)
        let anno = MKPointAnnotation()
        anno.coordinate = coordinate
        uiView.addAnnotation(anno)
        let region = MKCoordinateRegion(center: coordinate, latitudinalMeters: 2000, longitudinalMeters: 2000)
        uiView.setRegion(region, animated: false)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject {
        var parent: MapPickerRepresentable
        init(_ parent: MapPickerRepresentable) { self.parent = parent }

        @objc func mapTapped(_ gesture: UITapGestureRecognizer) {
            guard let mapView = gesture.view as? MKMapView else { return }
            let point = gesture.location(in: mapView)
            let coord = mapView.convert(point, toCoordinateFrom: mapView)
            parent.onTap(coord)
        }
    }
}
