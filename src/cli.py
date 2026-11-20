"""Command-Line Interface Prototype for Course Waitlist Service.

Provides both an interactive REPL and an automated walkthrough mode
demonstrating the complete enrollment, waitlist, and FIFO promotion lifecycle.
"""

import sys
from typing import Set

from src.models import CourseOffering, Learner
from src.service import CourseWaitlistService


def create_demo_service() -> CourseWaitlistService:
    """Initialize a course offering with prerequisites and starter learners."""
    offering = CourseOffering(
        id="CS201",
        title="Data Structures & Algorithms",
        capacity=2,
        prerequisites={"CS101"},
    )
    service = CourseWaitlistService(offering)

    # Register initial pool of learners
    service.register_learner(Learner(id="alice", name="Alice Chen", completed_courses={"CS101"}))
    service.register_learner(Learner(id="bob", name="Bob Smith", completed_courses={"CS101"}))
    service.register_learner(Learner(id="carol", name="Carol Danvers", completed_courses=set()))  # Missing CS101
    service.register_learner(Learner(id="david", name="David Miller", completed_courses={"CS101"}))
    service.register_learner(Learner(id="eve", name="Eve Johnson", completed_courses={"CS101"}))

    return service


def print_status(service: CourseWaitlistService):
    """Format and print current offering snapshot."""
    snap = service.get_snapshot()
    print("\n" + "=" * 60)
    print(f"Course: {snap.title} ({snap.offering_id})")
    print(f"Capacity: {snap.capacity} | Enrolled: {snap.enrolled_count} | Available Seats: {snap.available_seats}")
    print(f"Prerequisites: {', '.join(sorted(service.offering.prerequisites)) or 'None'}")
    print(f"Enrolled Learners: {snap.enrolled_learners}")
    print(f"Waitlist (FIFO):   {snap.waitlist}")
    print("=" * 60 + "\n")


def run_automated_demo():
    """Execute a self-verifying end-to-end demonstration."""
    print("\n" + "#" * 60)
    print("  COURSE WAITLIST SERVICE — AUTOMATED ACCEPTANCE DEMO")
    print("#" * 60)

    service = create_demo_service()
    print_status(service)

    print("--- STEP 1: Enroll Alice (has CS101, seat available) ---")
    res1 = service.enroll("alice")
    print(f"Result: {res1.status.value} -> {res1.message}")
    assert res1.status.value == "ENROLLED"

    print("\n--- STEP 2: Enroll Bob (has CS101, reaches capacity 2/2) ---")
    res2 = service.enroll("bob")
    print(f"Result: {res2.status.value} -> {res2.message}")
    assert res2.status.value == "ENROLLED"

    print("\n--- STEP 3: Duplicate enrollment attempt by Alice ---")
    res_dup = service.enroll("alice")
    print(f"Result: {res_dup.status.value} -> {res_dup.message}")
    assert res_dup.status.value == "ALREADY_ENROLLED"

    print("\n--- STEP 4: Enroll Carol (lacks CS101 prerequisite) ---")
    res_carol = service.enroll("carol")
    print(f"Result: {res_carol.status.value} -> {res_carol.message}")
    assert res_carol.status.value == "PREREQUISITE_NOT_MET"

    print("\n--- STEP 5: Enroll David (has CS101, course is full -> joins waitlist #1) ---")
    res_david = service.enroll("david")
    print(f"Result: {res_david.status.value} -> {res_david.message} (Pos #{res_david.waitlist_position})")
    assert res_david.status.value == "WAITLISTED" and res_david.waitlist_position == 1

    print("\n--- STEP 6: Enroll Eve (has CS101, joins waitlist #2) ---")
    res_eve = service.enroll("eve")
    print(f"Result: {res_eve.status.value} -> {res_eve.message} (Pos #{res_eve.waitlist_position})")
    assert res_eve.status.value == "WAITLISTED" and res_eve.waitlist_position == 2

    print("\n--- STEP 7: Duplicate enrollment attempt on waitlist by David ---")
    res_david_dup = service.enroll("david")
    print(f"Result: {res_david_dup.status.value} -> {res_david_dup.message}")
    assert res_david_dup.status.value == "ALREADY_WAITLISTED"

    print_status(service)

    print("--- STEP 8: Drop Alice (Opens seat -> David promoted FIFO) ---")
    drop_res1 = service.drop("alice")
    print(f"Result: {drop_res1.status.value} -> {drop_res1.message}")
    print(f"Promoted: {drop_res1.promoted_learner_id}")
    assert drop_res1.status.value == "DROPPED_AND_PROMOTED"
    assert drop_res1.promoted_learner_id == "david"

    print_status(service)

    print("--- STEP 9: Repeat Drop of Alice (already dropped) ---")
    drop_res_repeat = service.drop("alice")
    print(f"Result: {drop_res_repeat.status.value} -> {drop_res_repeat.message}")
    assert drop_res_repeat.status.value == "NOT_ENROLLED"

    print("\n--- STEP 10: Ineligible Waitlist Promotion Handling (Skip-and-Retain) ---")
    # Grant Carol a waitlist spot (simulating status change or condition) and drop Bob
    # Let's register Frank with missing prereq, simulate waitlist
    service.register_learner(Learner(id="frank", name="Frank", completed_courses=set()))
    # Directly inject Frank into waitlist ahead of Eve to test skip policy
    service._waitlist.insert(0, "frank")
    print(f"Current waitlist before drop: {service.get_snapshot().waitlist}")

    drop_bob = service.drop("bob")
    print(f"Bob dropped: {drop_bob.status.value} -> {drop_bob.message}")
    print(f"Promoted: {drop_bob.promoted_learner_id}")
    print(f"Waitlist after drop: {service.get_snapshot().waitlist}")
    # Frank was skipped because he lacks CS101, but Frank is RETAINED on waitlist. Eve was promoted!
    assert drop_bob.promoted_learner_id == "eve"
    assert "frank" in service.get_snapshot().waitlist

    print_status(service)
    print("DEMO COMPLETE: All acceptance criteria verified successfully!\n")


def interactive_repl():
    """Start interactive command prompt."""
    service = create_demo_service()
    print("\n=== Course Waitlist Service Interactive CLI ===")
    print("Commands: status | enroll <id> | drop <id> | register <id> <name> [prereqs...] | demo | exit")

    while True:
        try:
            raw = input("waitlist-cli> ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nExiting.")
            break

        if not raw:
            continue

        parts = raw.split()
        cmd = parts[0].lower()

        if cmd in ("exit", "quit"):
            print("Goodbye.")
            break
        elif cmd == "status":
            print_status(service)
        elif cmd == "demo":
            run_automated_demo()
        elif cmd == "enroll":
            if len(parts) < 2:
                print("Usage: enroll <learner_id>")
                continue
            res = service.enroll(parts[1])
            print(f"[{res.status.value}] {res.message}")
        elif cmd == "drop":
            if len(parts) < 2:
                print("Usage: drop <learner_id>")
                continue
            res = service.drop(parts[1])
            print(f"[{res.status.value}] {res.message}")
        elif cmd == "register":
            if len(parts) < 3:
                print("Usage: register <learner_id> <name> [completed_courses...]")
                continue
            l_id = parts[1]
            name = parts[2]
            prereqs = set(parts[3:]) if len(parts) > 3 else set()
            service.register_learner(Learner(id=l_id, name=name, completed_courses=prereqs))
            print(f"Registered learner '{l_id}' ({name}) with completed: {prereqs}")
        else:
            print(f"Unknown command: '{cmd}'. Try: status, enroll, drop, register, demo, exit")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--demo":
        run_automated_demo()
    else:
        interactive_repl()
