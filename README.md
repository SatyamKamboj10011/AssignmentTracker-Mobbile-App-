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


System Workflow
EduTrack distinguishes between three major user roles — Admin, Teacher, and Student. Each role has a unique set of actions, ensuring secure and controlled assignment management.

Admin Workflow
Purpose: System configuration and access management.
Admin can:
•	Create invite codes for new teachers/admin who wish to register
•	Approve or assign roles to authenticated users
•	Monitor system usage and ensure user-role validity
•	View announcements and assignments, but cannot modify academic content
•	Create and Edit courses
•	Assign Courses to teachers
•	Enroll the Students to courses  

Teacher Workflow
Purpose: Manage academic content and evaluate student submissions.
Teacher can:
•	Create assignments with due dates and descriptions
•	Upload files (PDF, images, documents) via Cloudinary
•	Edit or delete assignments
•	Assign assignments to individual students or all students enrolled in that course
•	Assign the assignments to students dividing them into groups
•	View and grade submissions
•	Add private teacher notes on each assignment (visible only to teacher)
•	Create announcements visible to all enrolled students
•	Track assignment status and student engagement

     
         

     

Student Workflow
Purpose: Receive, manage, complete, and track assignments.
Student can:
•	View enrolled courses
•	See all active assignments with time left indicators
•	Download or open assignment files online
•	Submit files for evaluation (Cloudinary link stored automatically)
•	Save personal progress notes and mark progress steps (Read, Drafted, Finalized)
•	Track submission history and view received grades
•	Read announcements from teachers
•	Join or view group assignments when applicable
•	Interact with chatbot
•	Access the sketchpad
•	Track the assignments through the calendar tool

  \                      


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
