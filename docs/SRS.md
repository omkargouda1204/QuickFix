# 2. Software Requirements Specification (SRS)

## 2.1 Introduction
The Software Requirements Specification (SRS) document provides a detailed description of the QuickFix application's functional and non-functional requirements. Its purpose is to establish a clear understanding of the system's behavior for developers, stakeholders, and end-users.

## 2.2 Overall Description

### 2.2.1 Product Perspective
QuickFix is a standalone Android mobile application that integrates with several cloud-based services:
*   **Firebase:** For user authentication and real-time push notifications.
*   **Supabase:** As the primary relational database (PostgreSQL) and media storage.
*   **Razorpay:** For processing secure digital payments.
*   **Google Maps/Location API:** For geolocation services and provider tracking.

### 2.2.2 Product Functions
The main functions of the system include:
*   User registration and secure profile management.
*   Automatic and manual location detection for service filtering.
*   Searching and filtering service providers by category, distance, and ratings.
*   Real-time booking and scheduling of services.
*   In-app messaging between customers and service providers.
*   Digital payment processing for completed services.
*   Provider onboarding and business profile management.

### 2.2.3 User Classes and Characteristics
*   **Customers:** Tech-savvy users looking for convenient home services. They require a simple, fast, and secure interface.
*   **Service Providers:** Local professionals or businesses. They need tools to manage their schedule, view earnings, and communicate with clients.
*   **System Administrators:** Internal staff responsible for validating provider documents and managing platform integrity.

### 2.2.4 Operating Environment
*   **Platform:** Android OS 7.0 (API 24) or higher.
*   **Network:** Requires a stable internet connection (WiFi/4G/5G).
*   **Hardware:** Requires GPS capabilities for location-based features and a camera for uploading business images.

## 2.3 Special Requirements
*   **Offline Handling:** The application should notify the user when the internet connection is lost and attempt to retry failed API calls.
*   **Location Permissions:** The app must gracefully handle cases where users deny GPS permissions by allowing manual city selection.
*   **Data Privacy:** All user data, especially contact information and location history, must be handled securely according to standard privacy regulations.

## 2.4 Functional Requirements

### 2.4.1 Module 1: Authentication
*   Users must be able to sign up using an email address and password.
*   The system must support "Continue with Google" for faster onboarding.
*   The system must distinguish between "Customer" and "Provider" roles upon first login.
*   Secure password reset functionality must be available via email.

### 2.4.2 Module 2: Service Discovery & Search
*   Users can search for providers using keywords (e.g., "AC repair", "Plumber").
*   Results must be filterable by City or "Nearby" (within a 20-25km radius).
*   Providers should be categorized (Electrical, Plumbing, Cleaning, etc.) for easy browsing.
*   The search bar should provide suggestions as the user types.

### 2.4.3 Module 3: Booking & Tracking
*   Customers can book a service by selecting a provider and providing service details.
*   Real-time status updates: Pending -> Accepted -> In-Progress -> Completed.
*   Users must receive push notifications for every change in booking status.
*   A history of all past and current bookings must be accessible to both parties.

### 2.4.4 Module 4: Provider Dashboard & Onboarding
*   Providers must upload identity proof and business details for verification.
*   Providers can toggle their availability status (Available/Busy).
*   The dashboard must display professional stats, including total bookings and ratings.
*   Providers can manage their service catalog and pricing directly from the app.

### 2.4.5 Module 5: Communication & Payments
*   Real-time chat interface for discussing service requirements.
*   Integration with Razorpay for secure checkout.
*   Generation of digital receipts upon successful payment.
*   Rating and review system enabled after a service is marked as "Completed".

## 2.5 Design Constraints
*   **UI Standards:** Must adhere to Google's Material Design 3 guidelines.
*   **Language:** Developed primarily in Java for the Android client.
*   **Database:** Constraints imposed by Supabase's PostgreSQL structure and Row Level Security policies.
*   **Size:** The APK size should be optimized for devices with limited storage.

## 2.6 System Attributes
*   **Reliability:** The system should maintain 99.9% uptime for the booking and payment modules.
*   **Availability:** Data should be synchronized across devices in real-time.
*   **Security:** Use of SSL/TLS for all API communications and Firebase for secure auth tokens.
*   **Scalability:** The architecture (Supabase/Firebase) must support a growing number of simultaneous users.

## 2.7 Other Requirements
*   **Legal:** Terms and Conditions and Privacy Policy must be accessible within the app.
*   **Localization:** Support for Indian English and Hindi (if applicable) for broader accessibility in the target market.
