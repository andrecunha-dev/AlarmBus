import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "bus.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 80, height: 80)
                .foregroundColor(Color(red: 103/255, green: 80/255, blue: 164/255))

            Text("AlarmBus GPS")
                .font(.title)
                .fontWeight(.bold)

            Text("Alerta de Destino para iOS")
                .font(.subheadline)
                .foregroundColor(.gray)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
