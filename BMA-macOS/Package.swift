// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BMA",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(
            name: "BMA",
            targets: ["BMA"]
        )
    ],
    dependencies: [
        // Vapor for HTTP server
        .package(url: "https://github.com/vapor/vapor.git", from: "4.89.0"),
        // Swift NIO SSL for HTTPS support
        .package(url: "https://github.com/apple/swift-nio-ssl.git", from: "2.24.0"),
    ],
    targets: [
        .executableTarget(
            name: "BMA",
            dependencies: [
                .product(name: "Vapor", package: "vapor"),
                .product(name: "NIOSSL", package: "swift-nio-ssl"),
            ],
            path: "Sources"
        ),
        .testTarget(
            name: "BMATests",
            dependencies: ["BMA"],
            path: "Tests"
        )
    ]
) 