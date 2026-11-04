import React from 'react';
import {createRoot} from 'react-dom/client';
import App from './App';
import '../style.css';
import './react.css';

const root=document.getElementById('root');
if(!root)throw Error('Quest Studio mount point not found');
createRoot(root).render(<React.StrictMode><App/></React.StrictMode>);
