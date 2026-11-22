// Sample application for REA testing
console.log('Hello from REA test app');

// Function that calculates something
function calculateTotal(items) {
  return items.reduce((sum, item) => sum + item.price, 0);
}

// User login simulation
const users = [
  {id: 1, name: 'Alice', role: 'admin'},
  {id: 2, name: 'Bob', role: 'user'}
];

function login(username, password) {
  const user = users.find(u => u.name === username);
  if (user && password === 'secret') {
    return {success: true, user};
  }
  return {success: false};
}

// API endpoint simulation
function handleRequest(path, data) {
  if (path === '/api/users') {
    return {status: 200, data: users};
  }
  if (path === '/api/login') {
    return login(data.username, data.password);
  }
  return {status: 404};
}

// Error handling
try {
  const result = calculateTotal([{price: 10}, {price: 20}]);
  console.log('Calculation result:', result);
} catch (error) {
  console.error('Error:', error.message);
}

console.log('App initialized successfully');