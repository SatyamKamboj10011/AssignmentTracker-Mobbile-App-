Assignment Tracker – Mobile Application

Developed by: Satyam Kamboj 
Institution: OPAIC Auckland, New Zealand 
Course Code: IA721001 – Mobile Application 
Development Year: 2025

📱 Overview

Assignment Tracker is an Android mobile application designed to streamline communication and assessment workflows between Students, Teachers, and Administrators. The system supports assignment distribution, progress tracking, submissions, feedback, course management, announcements, and notifications — all powered by a cloud-based backend.

🎯 Key Features ✅ For Students

View assignments by enrolled courses

Track assignment status and deadlines

Save personal progress notes (Room DB)

View progress timeline history

Submit assignments with file uploads (Cloudinary integration)

Receive announcements and notifications

👨‍🏫 For Teachers

Create and manage courses

Create, update, and delete assignments

Add group work & assign group members

View and grade student submissions

Add private teacher notes on assignments

Create course-based announcements

Remove participants from courses

🛡 For Admin

Manage teacher accounts and invite codes

Approve new teacher registrations

Manage roles (student/teacher/admin)

🏗 System Architecture

The system follows a multi-layer cloud architecture:

Android App (UI + Business Logic) ↓ Firebase Authentication ↓ Firebase Firestore (Courses, Users, Assignments, Submissions, Announcements) ↓ Cloudinary (File Uploads: PDFs, Docs, Images) ↓ Room Database (Local offline student progress storage)

🗂 Database Design Firestore Collections Collection Purpose users Stores user profiles, roles, photo, email courses Course details, participants list assignments Assignment title, due date, file URL, notes submissions Stored per assignment → per student announcements Course-level broadcast messages inviteCodes Admin generates teacher invite codes Room Database (Local)

Used for Student Progress Tracking

Saves draft notes, completion steps, timestamp logs

Enables offline persistence

🧩 Major Components Component Description Firebase Auth Login for all users Firestore Real-time backend Cloudinary Stores assignment attachments Notification Manager Sends reminders Room DB Offline notes for students 🚀 Application Workflow Student Workflow Login → Dashboard → View courses → View assignments → Track progress → Submit files → View grades → View announcements

Teacher Workflow Login → Manage courses → Create assignments → Add groups → Review submissions → Grade students → Create announcements

Admin Workflow Login → Generate invite codes → Approve teachers → Manage platform

🔧 Tech Stack Technology Purpose Java (Android) Core app development Firebase Auth + Firestore DB Cloudinary File hosting Glide Profile image loading RecyclerView Lists UIs Room DB Local student notes 📦 Project Structure /app/java/com.satyam.assignmenttracker activities/ adapters/ models/ firebase/ roomdb/ res/layout/... res/drawable/

Organized for scalability and modular function separation.

🔐 Security

✔ Role-based access (Student / Teacher / Admin) ✔ Admin invite code for teacher account creation ✔ Cloud-stored user roles, preventing unauthorized access ✔ Private teacher notes are hidden from students

📝 Future Enhancements

Push notifications for deadlines

Analytics dashboard for teachers

Export reports as PDF

AI-based plagiarism detection

🏁 Conclusion

Assignment Tracker successfully implements a full academic workflow system using authenticated user roles, real-time data management, assignment lifecycle features, progress tracking, and cloud storage. It demonstrates strong architectural design aligned with the course rubric and real-world scalability.

©️ Author

Satyam Kamboj 
Assignment Tracker – Mobile App 
OPAIC Auckland, NZ — 2025
