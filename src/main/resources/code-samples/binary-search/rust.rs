fn binary_search(values: &[i32], target: i32) -> Option<usize> {
    let mut low: isize = 0;
    let mut high: isize = values.len() as isize - 1;
    while low <= high {
        let mid = low + (high - low) / 2;
        let value = values[mid as usize];
        if value == target {
            return Some(mid as usize);
        } else if value < target {
            low = mid + 1;
        } else {
            high = mid - 1;
        }
    }
    None
}

fn main() {
    let values = [2, 5, 8, 12, 16, 23, 38, 56, 72, 91];
    let target = 23;
    match binary_search(&values, target) {
        Some(index) => println!("Found {} at index {}", target, index),
        None => println!("{} not found", target),
    }
}
